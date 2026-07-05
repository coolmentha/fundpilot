import asyncio
from playwright.async_api import async_playwright

URL = "https://www.fundpilot.duckdns.org/funds/1"
OUT = "/tmp/kline"

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        ctx = await browser.new_context(viewport={"width": 1440, "height": 900}, device_scale_factor=2)
        page = await ctx.new_page()
        # collect console errors
        errs = []
        page.on("console", lambda m: errs.append(m.text) if m.type == "error" else None)
        page.on("pageerror", lambda e: errs.append(f"PAGEERROR: {e}"))
        await page.goto(URL, wait_until="domcontentloaded", timeout=60000)
        # fund detail loads via API; wait for tabs to appear, then click 行情指标 tab
        await page.wait_for_selector(".ant-tabs-tab", timeout=45000)
        await page.locator(".ant-tabs-tab", has_text="行情指标").click()
        # wait for kline chart canvas
        await page.wait_for_selector(".kline-container canvas", timeout=45000)
        await page.wait_for_timeout(2500)  # let indicators render

        # 1. baseline: daily + MA5/10/20/30 + VOL
        await page.locator(".kline-chart-wrap").screenshot(path=f"{OUT}-1-baseline.png")

        # DOM checks
        ma_tags = await page.locator(".kline-ma-tag").all_inner_texts()
        active_ma = await page.locator(".kline-ma-tag.active").all_inner_texts()
        canvases = await page.locator(".kline-container canvas").count()
        print(f"[MA tags] all={ma_tags} active={active_ma} canvases={canvases}")

        # 2. enable MA250 + MA120 (click tags)
        for label in ["250", "120"]:
            await page.locator(f".kline-ma-tag", has_text=label).click()
            await page.wait_for_timeout(800)
        await page.locator(".kline-chart-wrap").screenshot(path=f"{OUT}-2-ma250.png")
        active_ma2 = await page.locator(".kline-ma-tag.active").all_inner_texts()
        print(f"[after click] active={active_ma2}")

        # 3. switch sub indicator to MACD
        await page.locator(".ant-segmented-item", has_text="MACD").click()
        await page.wait_for_timeout(2000)
        await page.locator(".kline-chart-wrap").screenshot(path=f"{OUT}-3-macd.png")
        canvases2 = await page.locator(".kline-container canvas").count()
        print(f"[MACD] canvases={canvases2} (2 panes expected: main + sub)")

        # 4. hover over candle area to trigger crosshair + tooltip
        box = await page.locator(".kline-container").bounding_box()
        # hover near a candle in the middle
        await page.mouse.move(box["x"] + box["width"] * 0.5, box["y"] + box["height"] * 0.35)
        await page.wait_for_timeout(1200)
        await page.locator(".kline-chart-wrap").screenshot(path=f"{OUT}-4-hover.png")

        # 5. hover at a different x to show crosshair moves
        await page.mouse.move(box["x"] + box["width"] * 0.3, box["y"] + box["height"] * 0.4)
        await page.wait_for_timeout(1200)
        await page.locator(".kline-chart-wrap").screenshot(path=f"{OUT}-5-hover2.png")

        print(f"[console errors] {errs[:10]}")
        await browser.close()

asyncio.run(main())
