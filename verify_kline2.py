import asyncio
from playwright.async_api import async_playwright

URL = "https://www.fundpilot.duckdns.org/funds/1"

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        ctx = await browser.new_context(viewport={"width": 1440, "height": 900}, device_scale_factor=1)
        page = await ctx.new_page()
        pageerrors = []
        page.on("pageerror", lambda e: pageerrors.append(f"{e}\n---STACK---\n{e.stack}"))
        await page.goto(URL, wait_until="domcontentloaded", timeout=60000)
        await page.wait_for_selector(".ant-tabs-tab", timeout=45000)
        await page.locator(".ant-tabs-tab", has_text="行情指标").click()
        await page.wait_for_selector(".kline-container canvas", timeout=45000)
        await page.wait_for_timeout(3000)

        # pixel analysis: per canvas, count colored (non-bg) pixels + red/green/amber/blue
        stats = await page.evaluate("""() => {
            const canvases = document.querySelectorAll('.kline-container canvas');
            const out = [];
            for (const c of canvases) {
                const ctx = c.getContext('2d');
                let w = c.width, h = c.height;
                // device scale: actual pixels
                const img = ctx.getImageData(0, 0, w, h).data;
                let nonBg = 0, red = 0, green = 0, amber = 0, blue = 0, total = 0;
                for (let i = 0; i < img.length; i += 4) {
                    const r=img[i], g=img[i+1], b=img[i+2];
                    total++;
                    // background ~ #1E293B (30,41,59) or grid
                    if (!(r<60 && g<70 && b<80)) nonBg++;
                    if (r>180 && g<110 && b<110) red++;
                    if (g>150 && r<120 && b<140) green++;
                    if (r>200 && g>130 && b<80) amber++;
                    if (b>180 && r<120 && g<150) blue++;
                }
                out.push({w, h, nonBg: Math.round(nonBg/total*1000)/10, red, green, amber, blue});
            }
            return out;
        }""")
        print("=== BASELINE (VOL) canvas pixel stats (% non-bg + color counts) ===")
        for i, s in enumerate(stats):
            print(f"  canvas[{i}] {s['w']}x{s['h']} nonBg={s['nonBg']}% red={s['red']} green={s['green']} amber={s['amber']} blue={s['blue']}")

        # switch to MACD
        await page.locator(".ant-segmented-item", has_text="MACD").click()
        await page.wait_for_timeout(2500)
        stats2 = await page.evaluate("""() => {
            const canvases = document.querySelectorAll('.kline-container canvas');
            const out = [];
            for (const c of canvases) {
                const ctx = c.getContext('2d');
                let w = c.width, h = c.height;
                const img = ctx.getImageData(0, 0, w, h).data;
                let nonBg = 0, red = 0, green = 0, blue = 0, total = 0;
                for (let i = 0; i < img.length; i += 4) {
                    const r=img[i], g=img[i+1], b=img[i+2];
                    total++;
                    if (!(r<60 && g<70 && b<80)) nonBg++;
                    if (r>180 && g<110 && b<110) red++;
                    if (g>150 && r<120 && b<140) green++;
                    if (b>180 && r<120 && g<150) blue++;
                }
                out.push({w, h, nonBg: Math.round(nonBg/total*1000)/10, red, green, blue});
            }
            return out;
        }""")
        print("=== MACD canvas pixel stats ===")
        for i, s in enumerate(stats2):
            print(f"  canvas[{i}] {s['w']}x{s['h']} nonBg={s['nonBg']}% red={s['red']} green={s['green']} blue={s['blue']}")

        # hover to trigger crosshair + tooltip
        box = await page.locator(".kline-container").bounding_box()
        await page.mouse.move(box["x"] + box["width"]*0.5, box["y"] + box["height"]*0.3)
        await page.wait_for_timeout(1500)
        stats3 = await page.evaluate("""() => {
            const canvases = document.querySelectorAll('.kline-container canvas');
            let totalNonBg = 0;
            for (const c of canvases) {
                const ctx = c.getContext('2d');
                const img = ctx.getImageData(0, 0, c.width, c.height).data;
                for (let i = 0; i < img.length; i += 4) {
                    const r=img[i], g=img[i+1], b=img[i+2];
                    if (!(r<60 && g<70 && b<80)) totalNonBg++;
                }
            }
            return totalNonBg;
        }""")
        print(f"=== HOVER total non-bg pixels (crosshair+tooltip should add some): {stats3} ===")

        print("\n=== PAGE ERRORS ===")
        for e in pageerrors:
            print(e)
            print("========")

        await browser.close()

asyncio.run(main())
