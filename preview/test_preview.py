"""Exercise the offline prototype in installed Chrome and capture design screenshots.
Requires websocket-client (see requirements-dev.txt); never starts the Android app.
"""
from pathlib import Path
import base64
import json
import os
import shutil
import subprocess
import tempfile
import time
import urllib.request

import websocket

ROOT = Path(__file__).resolve().parent
CHROME = os.environ.get("CHROME_BIN") or shutil.which("google-chrome") or shutil.which("chromium") or "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


def main():
    screenshots = ROOT / "screenshots"
    screenshots.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="wingai-preview-") as profile:
        process = subprocess.Popen([
            CHROME, "--headless=new", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
            "--disable-background-networking", "--disable-component-update", "--disable-sync",
            "--remote-debugging-port=0", "--remote-allow-origins=http://localhost",
            f"--user-data-dir={profile}", "about:blank"
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        ws = None
        try:
            port_file = Path(profile) / "DevToolsActivePort"
            for _ in range(100):
                if port_file.exists():
                    break
                if process.poll() is not None:
                    raise RuntimeError("Chrome exited before the preview could be tested")
                time.sleep(.1)
            port = int(port_file.read_text().splitlines()[0])
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list") as response:
                page = next(item for item in json.load(response) if item["type"] == "page")
            ws = websocket.create_connection(page["webSocketDebuggerUrl"], origin="http://localhost", timeout=15)
            sequence = 0
            errors, external_requests = [], []

            def command(method, params=None):
                nonlocal sequence
                sequence += 1
                ws.send(json.dumps({"id": sequence, "method": method, "params": params or {}}))
                while True:
                    reply = json.loads(ws.recv())
                    if reply.get("method") == "Runtime.exceptionThrown":
                        errors.append(reply["params"])
                    if reply.get("method") == "Network.requestWillBeSent":
                        url = reply["params"]["request"]["url"]
                        if url.startswith(("https://", "http://")):
                            external_requests.append(url)
                    if reply.get("id") == sequence:
                        if "error" in reply:
                            raise RuntimeError(reply["error"])
                        return reply.get("result", {})

            def evaluate(source):
                result = command("Runtime.evaluate", {"expression": source, "returnByValue": True, "awaitPromise": True})
                if "exceptionDetails" in result:
                    raise AssertionError(result["exceptionDetails"])
                return result.get("result", {}).get("value")

            def check(name, expression):
                assert evaluate(expression), name
                print("PASS", name)

            def viewport(width, height=1100):
                command("Emulation.setDeviceMetricsOverride", {"width": width, "height": height, "deviceScaleFactor": 1, "mobile": False})

            def screenshot(name, selector=None):
                params = {"format": "png", "captureBeyondViewport": True}
                if selector:
                    bounds = evaluate(f"(() => {{const r=document.querySelector({json.dumps(selector)}).getBoundingClientRect();return {{x:r.x+scrollX,y:r.y+scrollY,width:r.width,height:r.height,scale:2}};}})()")
                    params["clip"] = bounds
                else:
                    size = command("Page.getLayoutMetrics")["cssContentSize"]
                    params["clip"] = {"x": 0, "y": 0, "width": size["width"], "height": size["height"], "scale": 1}
                image = command("Page.captureScreenshot", params)
                (screenshots / name).write_bytes(base64.b64decode(image["data"]))

            command("Page.enable"); command("Runtime.enable"); command("Network.enable")
            viewport(1440, 1200)
            command("Page.navigate", {"url": (ROOT / "index.html").as_uri()})
            for _ in range(100):
                if evaluate("document.querySelectorAll('.reply-card').length === 3"):
                    break
                time.sleep(.1)
            check("initial sample replies", "document.querySelectorAll('.reply-card').length===3")
            check("Analyze chat label replaces Read chat", "document.querySelector('#analyze-demo').textContent==='Analyze chat' && !document.body.textContent.includes('Read chat')")
            key_colors = set()
            for theme in ["aurora", "daylight", "ocean", "rose"]:
                evaluate(f"document.querySelector('[data-theme-choice={theme}]').click()")
                check(f"{theme} selection updates both screens", f"[...document.querySelectorAll('.phone')].every(p=>p.dataset.theme==='{theme}') && document.querySelectorAll('[data-theme-choice={theme}][aria-pressed=true]').length===2")
                screenshot(f"{theme}-reply-mockup.png", "#conversation-phone")
                evaluate("document.querySelector('#toggle-keys').click()")
                key_colors.add(evaluate("getComputedStyle(document.querySelector('.key-row button')).backgroundColor"))
                check(f"{theme} themed letter key and readable label", "getComputedStyle(document.querySelector('.key-row button')).backgroundColor===getComputedStyle(document.querySelector('.plan-card')).backgroundColor && getComputedStyle(document.querySelector('.key-row button')).color===getComputedStyle(document.querySelector('.phone')).color")
                check(f"{theme} space and function keys have separate surfaces", "getComputedStyle(document.querySelector('.space-key')).backgroundColor!==getComputedStyle(document.querySelector('.key-row button')).backgroundColor && getComputedStyle(document.querySelector('.wide-key')).backgroundColor!==getComputedStyle(document.querySelector('.key-row button')).backgroundColor")
                check(f"{theme} action key matches primary reply action", "getComputedStyle(document.querySelector('.action-key')).backgroundColor===getComputedStyle(document.querySelector('.insert')).backgroundColor")
                screenshot(f"{theme}-keyboard-mockup.png", "#conversation-phone")
                evaluate("document.querySelector('#toggle-keys').click()")
            assert len(key_colors) == 4, "Every theme must change the keyboard keys"
            evaluate("document.querySelector('#analyze-demo').click()")
            check("Analyze chat retains its sample analysis action", "document.querySelectorAll('.loading-bar').length>0")
            time.sleep(1)
            command("Page.reload")
            time.sleep(.3)
            check("theme persists after reload", "document.querySelector('.phone').dataset.theme==='rose'")
            for tone in ["all", "friendly", "casual", "professional", "funny", "flirty", "short", "detailed"]:
                evaluate(f"document.querySelector('[data-tone={tone}]').click()")
                check(f"{tone} sample tone", "document.querySelectorAll('.reply-card').length===3 && document.querySelectorAll('[data-tone][aria-pressed=true]').length===1")
            evaluate("document.querySelector('.insert').click()")
            check("insert changes draft only", "document.querySelector('#draft').value.length>0 && document.querySelectorAll('.message').length===3")
            evaluate("document.querySelector('.reply-actions button').click();document.querySelector('.insert').click()")
            check("shorten produces sample short reply", "document.querySelector('#draft').value==='Yep, see you at 4!'")
            evaluate("document.querySelectorAll('.reply-actions button')[1].click();document.querySelector('.insert').click()")
            check("expand produces longer sample reply", "document.querySelector('#draft').value.length>90")
            evaluate("document.querySelector('#send-demo').click()")
            check("send never sends a message", "document.querySelectorAll('.message').length===3 && document.querySelector('#toast').textContent.includes('never sent')")
            for mode in ["empty", "loading", "error"]:
                evaluate(f"document.querySelector('#demo-state').value='{mode}';document.querySelector('#demo-state').dispatchEvent(new Event('change'))")
                check(f"{mode} state renders", "document.querySelector('#reply-list').textContent.trim().length>0")
            evaluate("document.querySelector('.reply-empty button').click()")
            check("retry starts loading", "document.querySelectorAll('.loading-bar').length>0")
            time.sleep(1)
            check("retry finishes with local samples", "document.querySelectorAll('.reply-card').length===3 && !document.querySelector('.loading-bar')")
            evaluate("document.querySelector('#draft').value='';document.querySelector('#toggle-keys').click();document.querySelector('.key-row button').click()")
            check("demo keyboard types", "document.querySelector('#draft').value==='q' && document.querySelector('#reply-panel').hidden")
            evaluate("document.querySelector('.key-row:last-child button:last-child').click()")
            check("demo backspace works", "document.querySelector('#draft').value===''")
            evaluate("document.querySelector('[data-screen=setup]').click();document.querySelectorAll('[data-setup]').forEach(b=>b.click())")
            check("setup actions are explicitly simulated", "document.querySelectorAll('.setup-progress .done').length===3 && document.querySelector('#toast').textContent.includes('No device permission')")
            screenshot("setup-mockup.png", "#conversation-phone")
            evaluate("document.querySelector('#reset-demo').click()")
            check("reset clears sample state", "document.querySelector('.phone').dataset.theme==='aurora' && document.querySelector('#draft').value==='' && document.querySelector('#setup-screen').hidden && !document.querySelector('#reply-panel').hidden")
            evaluate("document.querySelector('#toast').classList.remove('visible')")
            for width in [1440, 1200, 1024, 768, 720, 390, 360, 320]:
                viewport(width)
                check(f"no page overflow at {width}px", "document.documentElement.scrollWidth<=innerWidth")
                check(f"phones fit at {width}px", "[...document.querySelectorAll('.phone')].every(p=>p.getBoundingClientRect().right<=innerWidth+1 && p.getBoundingClientRect().left>=0)")
            viewport(390, 850); screenshot("mobile-page.png")
            viewport(1440, 1200); screenshot("aurora-desktop.png")
            screenshot("account-mockup.png", "#account-phone")
            evaluate("document.querySelector('#toggle-keys').click()")
            screenshot("full-keyboard-desktop.png")
            check("styles loaded", "getComputedStyle(document.querySelector('.phone')).borderRadius==='34px'")
            assert not errors, f"Browser exceptions: {errors}"
            assert not external_requests, f"Unexpected network activity: {external_requests}"
            print("PASS no browser exceptions or external web/API requests")
            print("Screenshots:", screenshots)
        finally:
            if ws:
                ws.close()
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait()


if __name__ == "__main__":
    main()
