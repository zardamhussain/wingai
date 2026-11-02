"""Generate the dependency-free Xcode app, keyboard and unit-test targets.
Outputs are deterministic. Refuse to overwrite a different project; preserve manual Xcode edits.
"""
from pathlib import Path
import hashlib
import json
import zlib
import struct
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OBJECTS = {}


def uid(label):
    return hashlib.sha256(label.encode()).hexdigest()[:24].upper()


def obj(label, isa, **fields):
    key = uid(label)
    if key in OBJECTS:
        raise ValueError(f"Duplicate object: {label}")
    OBJECTS[key] = {"isa": isa, **fields}
    return key


def plist(value, depth=0):
    if isinstance(value, dict):
        return "{\n" + "".join("\t" * (depth + 1) + json.dumps(k) + " = " + plist(v, depth + 1) + ";\n" for k, v in value.items()) + "\t" * depth + "}"
    if isinstance(value, list):
        return "(\n" + "".join("\t" * (depth + 1) + plist(v, depth + 1) + ",\n" for v in value) + "\t" * depth + ")"
    return json.dumps(str(value))


def create(path, contents):
    path.parent.mkdir(parents=True, exist_ok=True)
    data = contents.encode() if isinstance(contents, str) else contents
    if path.exists() and path.read_bytes() != data:
        raise RuntimeError(f"Refusing to overwrite modified generated file: {path}")
    path.write_bytes(data)


def main():
    shared = ["WingModels.swift", "WingAppearance.swift", "WingAiApiClient.swift", "WingAiSettings.swift", "WingAiDesignSystem.swift", "WingReplySession.swift"]
    app_sources = [f"Shared/{name}" for name in shared] + ["WingAIApp/AppMain.swift", "WingAIApp/WingAIApp.swift"]
    keyboard_sources = [f"Shared/{name}" for name in shared] + ["WingAIKeyboard/KeyboardViewController.swift", "WingAIKeyboard/WingAiKeyboardView.swift"]
    test_sources = ["Tests/WingAITests.swift"]
    refs = {}
    paths = list(dict.fromkeys(app_sources + keyboard_sources + test_sources))
    for path in paths:
        assert (ROOT / path).is_file(), path
        refs[path] = obj("file:" + path, "PBXFileReference", lastKnownFileType="sourcecode.swift", path=path, sourceTree="SOURCE_ROOT")
    for path, kind in [("Config/Common.xcconfig", "text.xcconfig"), ("Config/App-Info.plist", "text.plist.xml"), ("Config/Keyboard-Info.plist", "text.plist.xml"), ("Config/PrivacyInfo.xcprivacy", "text.xml"), ("Assets.xcassets", "folder.assetcatalog")]:
        refs[path] = obj("file:" + path, "PBXFileReference", lastKnownFileType=kind, path=path, sourceTree="SOURCE_ROOT")
    products = {}
    for target, suffix, kind in [("WingAI", "app", "wrapper.application"), ("WingAIKeyboard", "appex", "wrapper.app-extension"), ("WingAITests", "xctest", "wrapper.cfbundle")]:
        products[target] = obj("product:" + target, "PBXFileReference", explicitFileType=kind, includeInIndex=0, path=f"{target}.{suffix}", sourceTree="BUILT_PRODUCTS_DIR")
    products_group = obj("group:products", "PBXGroup", children=list(products.values()), name="Products", sourceTree="<group>")
    main_group = obj("group:root", "PBXGroup", children=list(refs.values()) + [products_group], sourceTree="<group>")

    def phase(target, name, isa, files):
        builds = [obj(f"build:{target}:{path}", "PBXBuildFile", fileRef=refs[path]) for path in files]
        return obj(f"phase:{target}:{name}", isa, buildActionMask=2147483647, files=builds, runOnlyForDeploymentPostprocessing=0)

    def configurations(label, settings):
        configs = []
        for mode in ("Debug", "Release"):
            build = dict(settings)
            build.update(SWIFT_OPTIMIZATION_LEVEL="-Onone" if mode == "Debug" else "-O", DEBUG_INFORMATION_FORMAT="dwarf" if mode == "Debug" else "dwarf-with-dsym")
            if mode == "Debug":
                build.update(ENABLE_TESTABILITY="YES", SWIFT_ACTIVE_COMPILATION_CONDITIONS="$(inherited) DEBUG", ONLY_ACTIVE_ARCH="YES")
            else:
                build.update(SWIFT_COMPILATION_MODE="wholemodule")
            configs.append(obj(f"config:{label}:{mode}", "XCBuildConfiguration", baseConfigurationReference=refs["Config/Common.xcconfig"], buildSettings=build, name=mode))
        return obj("configs:" + label, "XCConfigurationList", buildConfigurations=configs, defaultConfigurationIsVisible=0, defaultConfigurationName="Release")

    project_id = uid("project")
    def dependency(owner, target):
        proxy = obj(f"proxy:{owner}:{target}", "PBXContainerItemProxy", containerPortal=project_id, proxyType=1, remoteGlobalIDString=uid("target:" + target), remoteInfo=target)
        return obj(f"dependency:{owner}:{target}", "PBXTargetDependency", target=uid("target:" + target), targetProxy=proxy)

    targets = []
    for target, sources, product_type in [("WingAI", app_sources, "com.apple.product-type.application"), ("WingAIKeyboard", keyboard_sources, "com.apple.product-type.app-extension"), ("WingAITests", test_sources, "com.apple.product-type.bundle.unit-test")]:
        resources = [] if target == "WingAITests" else ["Config/PrivacyInfo.xcprivacy"]
        if target == "WingAI": resources.append("Assets.xcassets")
        phases = [phase(target, "sources", "PBXSourcesBuildPhase", sources), phase(target, "frameworks", "PBXFrameworksBuildPhase", []), phase(target, "resources", "PBXResourcesBuildPhase", resources)]
        settings = {"PRODUCT_NAME": "$(TARGET_NAME)", "SWIFT_EMIT_LOC_STRINGS": "YES", "CODE_SIGN_STYLE": "Automatic", "DEVELOPMENT_TEAM": "", "LD_RUNPATH_SEARCH_PATHS": ["$(inherited)", "@executable_path/Frameworks"]}
        dependencies = []
        if target == "WingAI":
            settings.update(PRODUCT_BUNDLE_IDENTIFIER="$(PRODUCT_BUNDLE_PREFIX)", INFOPLIST_FILE="Config/App-Info.plist", ASSETCATALOG_COMPILER_APPICON_NAME="AppIcon")
            embedded = obj("build:embed-keyboard", "PBXBuildFile", fileRef=products["WingAIKeyboard"], settings={"ATTRIBUTES": ["RemoveHeadersOnCopy"]})
            phases.append(obj("phase:embed-keyboard", "PBXCopyFilesBuildPhase", buildActionMask=2147483647, dstPath="", dstSubfolderSpec=13, files=[embedded], name="Embed App Extensions", runOnlyForDeploymentPostprocessing=0))
            dependencies.append(dependency(target, "WingAIKeyboard"))
        elif target == "WingAIKeyboard":
            settings.update(PRODUCT_BUNDLE_IDENTIFIER="$(PRODUCT_BUNDLE_PREFIX).keyboard", INFOPLIST_FILE="Config/Keyboard-Info.plist", APPLICATION_EXTENSION_API_ONLY="YES", SKIP_INSTALL="YES", LD_RUNPATH_SEARCH_PATHS=["$(inherited)", "@executable_path/Frameworks", "@executable_path/../../Frameworks"])
        else:
            settings.update(PRODUCT_BUNDLE_IDENTIFIER="$(PRODUCT_BUNDLE_PREFIX).tests", GENERATE_INFOPLIST_FILE="YES", BUNDLE_LOADER="$(TEST_HOST)", TEST_HOST="$(BUILT_PRODUCTS_DIR)/WingAI.app/WingAI", SKIP_INSTALL="YES")
            dependencies.append(dependency(target, "WingAI"))
        targets.append(obj("target:" + target, "PBXNativeTarget", buildConfigurationList=configurations(target, settings), buildPhases=phases, buildRules=[], dependencies=dependencies, name=target, productName=target, productReference=products[target], productType=product_type))
    project_configs = configurations("project", {"SDKROOT": "iphoneos", "SUPPORTED_PLATFORMS": "iphoneos iphonesimulator", "CLANG_ENABLE_MODULES": "YES", "CLANG_ENABLE_OBJC_ARC": "YES", "ENABLE_USER_SCRIPT_SANDBOXING": "YES", "GCC_C_LANGUAGE_STANDARD": "gnu17", "SUPPORTS_MACCATALYST": "NO"})
    obj("project", "PBXProject", attributes={"LastUpgradeCheck": "1600", "TargetAttributes": {t: {"CreatedOnToolsVersion": "16.0", "ProvisioningStyle": "Automatic"} for t in targets}}, buildConfigurationList=project_configs, compatibilityVersion="Xcode 14.0", developmentRegion="en", hasScannedForEncodings=0, knownRegions=["en", "Base"], mainGroup=main_group, productRefGroup=products_group, projectDirPath="", projectRoot="", targets=targets)
    text = "// !$*UTF8*$!\n" + plist({"archiveVersion": 1, "classes": {}, "objectVersion": 56, "objects": OBJECTS, "rootObject": project_id}) + "\n"
    create(ROOT / "WingAI.xcodeproj/project.pbxproj", text)

    scheme = ET.Element("Scheme", LastUpgradeVersion="1600", version="1.3")
    action = ET.SubElement(scheme, "BuildAction", parallelizeBuildables="YES", buildImplicitDependencies="YES")
    entries = ET.SubElement(action, "BuildActionEntries")
    entry = ET.SubElement(entries, "BuildActionEntry", buildForTesting="YES", buildForRunning="YES", buildForProfiling="YES", buildForArchiving="YES", buildForAnalyzing="YES")
    def reference(parent, target, suffix):
        ET.SubElement(parent, "BuildableReference", BuildableIdentifier="primary", BlueprintIdentifier=uid("target:" + target), BuildableName=target + suffix, BlueprintName=target, ReferencedContainer="container:WingAI.xcodeproj")
    reference(entry, "WingAI", ".app")
    testing = ET.SubElement(scheme, "TestAction", buildConfiguration="Debug", selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB", selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB", shouldUseLaunchSchemeArgsEnv="YES")
    testables = ET.SubElement(testing, "Testables")
    testable = ET.SubElement(testables, "TestableReference", skipped="NO")
    reference(testable, "WingAITests", ".xctest")
    launch = ET.SubElement(scheme, "LaunchAction", buildConfiguration="Debug", selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB", selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB", launchStyle="0", useCustomWorkingDirectory="NO", ignoresPersistentStateOnLaunch="NO", debugDocumentVersioning="YES", debugServiceExtension="internal", allowLocationSimulation="YES")
    reference(ET.SubElement(launch, "BuildableProductRunnable", runnableDebuggingMode="0"), "WingAI", ".app")
    profile = ET.SubElement(scheme, "ProfileAction", buildConfiguration="Release", shouldUseLaunchSchemeArgsEnv="YES", savedToolIdentifier="", useCustomWorkingDirectory="NO", debugDocumentVersioning="YES")
    reference(ET.SubElement(profile, "BuildableProductRunnable", runnableDebuggingMode="0"), "WingAI", ".app")
    ET.SubElement(scheme, "AnalyzeAction", buildConfiguration="Debug")
    ET.SubElement(scheme, "ArchiveAction", buildConfiguration="Release", revealArchiveInOrganizer="YES")
    ET.indent(scheme)
    create(ROOT / "WingAI.xcodeproj/xcshareddata/xcschemes/WingAI.xcscheme", ET.tostring(scheme, encoding="unicode", xml_declaration=True) + "\n")
    create(ROOT / "WingAI.xcodeproj/project.xcworkspace/contents.xcworkspacedata", '<?xml version="1.0" encoding="UTF-8"?>\n<Workspace version="1.0"><FileRef location="self:"/></Workspace>\n')
    create(ROOT / "Assets.xcassets/Contents.json", json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2) + "\n")
    create(ROOT / "Assets.xcassets/AppIcon.appiconset/Contents.json", json.dumps({"images": [{"filename": "AppIcon.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}], "info": {"author": "xcode", "version": 1}}, indent=2) + "\n")
    create(ROOT / "Assets.xcassets/AppIcon.appiconset/AppIcon.png", icon_png())
    print("Generated WingAI.xcodeproj: app + embedded keyboard + focused unit tests.")


def icon_png():
    # Original opaque app icon. iOS masks the square; do not pre-round its corners.
    size = 1024
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        for x in range(size):
            dx, dy = abs(x - 485), abs(y - 520)
            large = dx + 2.8 * dy < 280 or 2.8 * dx + dy < 310
            small = abs(x - 772) + 2.5 * abs(y - 236) < 94 or 2.5 * abs(x - 772) + abs(y - 236) < 100
            if large: color = (196, 181, 253)
            elif small: color = (125, 222, 208)
            else:
                lift = int(max(0, 1 - ((x - 450) ** 2 + (y - 430) ** 2) / 850000) * 18)
                color = (17 + lift, 16 + lift // 2, 27 + lift)
            raw.extend(color)
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')


if __name__ == "__main__":
    main()
