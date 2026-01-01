package cn.lwccxy.agentcraft.utils;

import lombok.RequiredArgsConstructor;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HtmlToPptService {

    private final HtmlRenderService renderService;

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    public byte[] convertSlidesToPpt(List<String> htmlSlides) {

        try (XMLSlideShow ppt = new XMLSlideShow()) {

            ppt.setPageSize(new Dimension(WIDTH, HEIGHT));

            for (String html : htmlSlides) {

                XSLFSlide slide = ppt.createSlide();

                // HTML → PNG
                byte[] image = renderService.htmlToImage(html);

                // PNG → PPT
                XSLFPictureData pd = ppt.addPicture(
                        image,
                        PictureData.PictureType.PNG
                );

                XSLFPictureShape pic = slide.createPicture(pd);
                pic.setAnchor(new Rectangle(0, 0, WIDTH, HEIGHT));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ppt.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("HTML转PPT失败", e);
        }
    }
}
