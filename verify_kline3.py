import asyncio
from playwright.async_api import async_playwright

URL = "https://www.fundpilot.duckdns.org/funds/1"

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        ctx = await browser.new_context(viewport={"width": 1440, "height": 900})
        page = await ctx.new_page()
        pageerrors = []
        page.on("pageerror", lambda e: pageerrors.append(f"{e}\nSTACK:\n{e.stack}"))
        await page.goto(URL, wait_until="domcontentloaded", timeout=60000)
        await page.wait_for_selector(".ant-tabs-tab", timeout=45000)
        await page.locator(".ant-tabs-tab", has_text="行情指标").click()
        await page.wait_for_selector(".kline-container canvas", timeout=45000)
        await page.wait_for_timeout(2000)

        # rapidly toggle MAs and switch sub to stress-test for the race
        for label in ["2", "250", "120", "60", "2", "250"]:
            await page.get_by_text(label, exact=True).click()
            await page.wait_for_timeout(150)
        for sub in ["MACD", "成交量", "MACD", "无", "成交量"]:
            await page.get_by_text(sub, exact=True).click()
            await page.wait_for_timeout(300)
        # switch period
        for lab in ["周K", "月K", "日K"]:
            await page.get_by_text(lab, exact=True).click()
            await page.wait_for_timeout(600)
        await page.wait_for_timeout(1500)

        print("=== PAGE ERRORS during stress ===")
        if not pageerrors:
            print("NONE")
        for e in pageerrors:
            print(e); print("====")
        await browser.close()

asyncio.run(main())
