"""Static project checks. These are NOT an Xcode build, XCTest run, or device test.
Optional --swift-grammar uses tree-sitter to parse source syntax without Apple SDKs.
"""
from pathlib import Path
import argparse
import json
import plistlib
import re
import struct
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--swift-grammar", action="store_true")
    args = parser.parse_args()
    checks = []
    def check(name, condition):
        assert condition, name
        checks.append(name)
        print("PASS", name)

    project = json.loads(subprocess.check_output(["/usr/bin/plutil", "-convert", "json", "-o", "-", str(ROOT / "WingAI.xcodeproj/project.pbxproj")]))
    objects = project["objects"]
    check("unique valid Xcode object IDs", all(re.fullmatch(r"[A-F0-9]{24}", value) for value in objects))
    def walk(value):
        if isinstance(value, dict):
            for item in value.values(): yield from walk(item)
        elif isinstance(value, list):
            for item in value: yield from walk(item)
        elif isinstance(value, str): yield value
    references = [value for value in walk(project) if re.fullmatch(r"[A-F0-9]{24}", value)]
    check("no dangling Xcode references", all(value in objects for value in references))
    for item in objects.values():
        if item["isa"] == "PBXFileReference" and item.get("sourceTree") == "SOURCE_ROOT":
            check("source exists: " + item["path"], (ROOT / item["path"]).exists())
    targets = {v["name"]: v for v in objects.values() if v["isa"] == "PBXNativeTarget"}
    check("app, keyboard and test targets present", set(targets) == {"WingAI", "WingAIKeyboard", "WingAITests"})
    def sources(target):
        result = []
        for phase_id in targets[target]["buildPhases"]:
            phase = objects[phase_id]
            if phase["isa"] == "PBXSourcesBuildPhase":
                result += [objects[objects[build]["fileRef"]]["path"] for build in phase["files"]]
        return result
    app, keyboard, tests = sources("WingAI"), sources("WingAIKeyboard"), sources("WingAITests")
    check("only host target includes app entry point", "WingAIApp/AppMain.swift" in app and "WingAIApp/AppMain.swift" not in keyboard)
    check("keyboard has its real UIKit entry point", "WingAIKeyboard/KeyboardViewController.swift" in keyboard)
    check("no host-only APIs included in keyboard", all("UIApplication" not in (ROOT / path).read_text() and "PhotosUI" not in (ROOT / path).read_text() for path in keyboard))
    check("all Swift source assigned once per target", all(len(group) == len(set(group)) for group in [app, keyboard, tests]))
    check("all shipping Swift source assigned to a target", {str(f.relative_to(ROOT)) for folder in ["Shared", "WingAIApp", "WingAIKeyboard"] for f in (ROOT / folder).glob("*.swift")} == set(app + keyboard))
    app_phases = [objects[value] for value in targets["WingAI"]["buildPhases"]]
    check("keyboard embedded in host PlugIns folder", any(p["isa"] == "PBXCopyFilesBuildPhase" and str(p["dstSubfolderSpec"]) == "13" and any(objects[objects[b]["fileRef"]]["path"] == "WingAIKeyboard.appex" for b in p["files"]) for p in app_phases))
    info = plistlib.loads((ROOT / "Config/Keyboard-Info.plist").read_bytes())
    extension = info["NSExtension"]
    check("keyboard extension registration", extension["NSExtensionPointIdentifier"] == "com.apple.keyboard-service")
    check("Full Access opt-in requested", extension["NSExtensionAttributes"]["RequestsOpenAccess"] is True)
    check("no default App Group entitlement requirement", not any("CODE_SIGN_ENTITLEMENTS" in item.get("buildSettings", {}) for item in objects.values()))
    for name in ["App-Info.plist", "Keyboard-Info.plist", "PrivacyInfo.xcprivacy", "AppGroups.entitlements.example"]:
        check("valid plist: " + name, isinstance(plistlib.loads((ROOT / "Config" / name).read_bytes()), dict))
    scheme = ET.parse(ROOT / "WingAI.xcodeproj/xcshareddata/xcschemes/WingAI.xcscheme").getroot()
    check("shared scheme references real targets", all(r.attrib["BlueprintIdentifier"] in objects for r in scheme.iter("BuildableReference")))
    check("focused tests included in shared scheme", scheme.find("TestAction/Testables/TestableReference") is not None)
    icon = (ROOT / "Assets.xcassets/AppIcon.appiconset/AppIcon.png").read_bytes()
    width, height, _, color_type = struct.unpack_from('>IIBB', icon, 16)
    check("1024px opaque app icon", (width, height, color_type) == (1024, 1024, 2))
    model = (ROOT / "Shared/WingReplySession.swift").read_text()
    controller = (ROOT / "WingAIKeyboard/KeyboardViewController.swift").read_text()
    check("request checks consent and Full Access", "guard networkAllowed" in model and "guard settings.hasServiceConsent" in model and "networkAllowed: self.hasFullAccess" in controller)
    check("Worker string limits use UTF-16", "context.utf16.count <= 12_000" in model and "instruction.utf16.count <= 500" in model)
    check("no old screen capture or private keyboard detection", all("ReplayKit" not in (ROOT / p).read_text() and 'value(forKey:' not in (ROOT / p).read_text() for p in set(app + keyboard)))
    if args.swift_grammar:
        from tree_sitter import Language, Parser
        import tree_sitter_swift
        swift = Parser(Language(tree_sitter_swift.language()))
        for path in sorted(ROOT.rglob("*.swift")):
            tree = swift.parse(path.read_bytes())
            if tree.root_node.has_error:
                def errors(node):
                    if node.type == "ERROR" or node.is_missing:
                        print(f"SYNTAX ERROR {path}:{node.start_point.row + 1}: {node.text[:160]!r}")
                    for child in node.children: errors(child)
                errors(tree.root_node)
            check("Swift grammar: " + str(path.relative_to(ROOT)), not tree.root_node.has_error)
    print(f"\n{len(checks)} static checks passed. Apple SDK compilation, signing, XCTest execution and device validation are NOT performed by this script.")


if __name__ == "__main__":
    main()
