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
public class ImageToPptService {

    /**
     * PPT尺寸（16:9）
     */
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    /**
     * 多图片转PPT
     *
     * @param images 图片二进制列表（PNG/JPG）
     */
    public byte[] convertImagesToPpt(List<byte[]> images) {

        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("图片不能为空");
        }

        try (XMLSlideShow ppt = new XMLSlideShow()) {

            // 设置PPT尺寸
            ppt.setPageSize(new Dimension(WIDTH, HEIGHT));

            for (byte[] image : images) {

                XSLFSlide slide = ppt.createSlide();

                PictureData.PictureType type = detectImageType(image);

                // 图片加入PPT
                XSLFPictureData pd = ppt.addPicture(image, type);

                XSLFPictureShape pic = slide.createPicture(pd);

                // 图片铺满页面
                pic.setAnchor(new Rectangle(0, 0, WIDTH, HEIGHT));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ppt.write(out);

            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("图片转PPT失败", e);
        }
    }

    /**
     * 自动检测图片类型（PNG / JPG）
     */
    private PictureData.PictureType detectImageType(byte[] image) {

        if (image.length < 4) {
            return PictureData.PictureType.PNG;
        }

        // PNG header
        if ((image[0] & 0xFF) == 0x89 &&
            image[1] == 0x50 &&
            image[2] == 0x4E &&
            image[3] == 0x47) {
            return PictureData.PictureType.PNG;
        }

        // JPG header
        if ((image[0] & 0xFF) == 0xFF &&
            (image[1] & 0xFF) == 0xD8) {
            return PictureData.PictureType.JPEG;
        }

        return PictureData.PictureType.PNG;
    }
}
