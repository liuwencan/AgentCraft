package cn.lwccxy.agentcraft.utils;

import com.microsoft.playwright.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class HtmlRenderService {

    public byte[] htmlToImage(String html) {

        try (Playwright playwright = Playwright.create()) {

            Browser browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions()
                            .setHeadless(true));

            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setViewportSize(1280, 720)
            );

            Page page = context.newPage();

            page.setContent(html);
            page.waitForTimeout(300); // 等待渲染

            return page.screenshot(
                    new Page.ScreenshotOptions()
                            .setFullPage(false)
            );
        }
    }
}
