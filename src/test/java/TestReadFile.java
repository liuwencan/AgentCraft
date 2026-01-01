import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestReadFile {

    public static void main(String[] args) throws IOException {
        Path path = Path.of("E:\\joke.txt");
        String s = readStringWithFallback(path);
        System.out.println(s);
    }

    /**
     * 使用编码回退机制读取文件内容。
     *
     * 尝试多种编码读取文件，处理不同编码格式的文件：
     * 1. 首先尝试 UTF-8（标准编码）
     * 2. 如果失败，尝试 GBK（Windows 中文环境）
     * 3. 如果还失败，尝试 ISO-8859-1（不会抛异常，但可能显示乱码）
     *
     * @param filePath 文件路径
     * @return 文件内容字符串
     * @throws IOException 如果所有编码尝试都失败
     */
    private static String readStringWithFallback(Path filePath) throws IOException {


        // 尝试 UTF-8
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (java.nio.charset.MalformedInputException e) {
            System.out.println("File is not UTF-8 encoded, trying GBK: " + filePath);
        }



        // 尝试 GBK (Windows 中文)
        try {
            return Files.readString(filePath, java.nio.charset.Charset.forName("GBK"));
        } catch (Exception e) {
            System.out.println("File is not UTF-8 encoded, trying GBK: " + filePath);
        }

        return "null";
//
//        // 最后尝试 ISO-8859-1（单字节编码，不会抛出异常）
//        byte[] bytes = Files.readAllBytes(filePath);
//        return new String(bytes, StandardCharsets.ISO_8859_1);
    }


}
