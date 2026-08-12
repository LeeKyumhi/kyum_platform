package com.guidematch.knowledge;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업로드 이미지 전처리.
 *
 * <p><b>EXIF 제거는 프라이버시 약속이고, 이 테스트가 그 약속의 유일한 증거다.</b>
 * 라이브러리가 알아서 해줄 것이라고 믿는 것과, 저장될 바이트에 실제로 없음을 확인한 것은 다르다.
 * 사용자는 자기 사진에 GPS와 기기 정보가 박혀 있다는 걸 대체로 모른다.
 */
class PlaceImageProcessorTest {

    private final PlaceImageProcessor processor = new PlaceImageProcessor();

    /** 지정 크기의 JPEG을 만든다. */
    private byte[] jpeg(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /**
     * JPEG에 EXIF APP1 세그먼트를 손으로 끼운다. 라이브러리 없이 EXIF 유무를 판별하려면
     * 마커를 직접 다루는 것이 가장 확실하다 — "Exif\0\0" 문자열의 존재로 판정한다.
     */
    private byte[] withExif(byte[] jpeg) {
        byte[] payload = "Exif\0\0MM\0*\0\0\0\bGPSInfoDeviceModel".getBytes(StandardCharsets.US_ASCII);
        int len = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg[0]); out.write(jpeg[1]);          // SOI (FF D8)
        out.write(0xFF); out.write(0xE1);               // APP1 마커
        out.write((len >> 8) & 0xFF); out.write(len & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(jpeg, 2, jpeg.length - 2);            // 원본 나머지
        return out.toByteArray();
    }

    private boolean containsExifMarker(byte[] data) {
        String s = new String(data, StandardCharsets.ISO_8859_1);
        return s.contains("Exif\0\0");
    }

    @Test
    void EXIF와_GPS가_저장물에서_사라진다() throws IOException {
        byte[] raw = withExif(jpeg(2400, 1800));
        assertThat(containsExifMarker(raw)).as("입력에는 EXIF가 있어야 테스트가 의미를 가진다").isTrue();

        PlaceImageProcessor.Processed p = processor.process(raw);

        assertThat(containsExifMarker(p.full())).as("full에 EXIF가 남아 있다").isFalse();
        assertThat(containsExifMarker(p.thumb())).as("thumb에 EXIF가 남아 있다").isFalse();
    }

    @Test
    void 긴_변이_1600과_400으로_줄어든다() throws IOException {
        PlaceImageProcessor.Processed p = processor.process(jpeg(2400, 1200));

        BufferedImage full = ImageIO.read(new ByteArrayInputStream(p.full()));
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(p.thumb()));

        assertThat(full.getWidth()).isEqualTo(PlaceImageProcessor.FULL_MAX_PX);
        assertThat(full.getHeight()).isEqualTo(800);      // 비율 유지 2:1
        assertThat(thumb.getWidth()).isEqualTo(PlaceImageProcessor.THUMB_MAX_PX);
        assertThat(thumb.getHeight()).isEqualTo(200);
    }

    @Test
    void 원본이_더_작으면_확대하지_않는다() throws IOException {
        PlaceImageProcessor.Processed p = processor.process(jpeg(300, 200));

        BufferedImage full = ImageIO.read(new ByteArrayInputStream(p.full()));
        assertThat(full.getWidth()).isEqualTo(300);
        assertThat(full.getHeight()).isEqualTo(200);
    }

    @Test
    void 용량이_실제로_줄어든다() throws IOException {
        // 폰 원본 4~8MB → 수백 KB. 목록에 15장을 깔 수 있게 되는 근거다.
        byte[] raw = jpeg(4000, 3000);
        PlaceImageProcessor.Processed p = processor.process(raw);

        assertThat(p.full().length).isLessThan(raw.length);
        assertThat(p.thumb().length).isLessThan(p.full().length);
    }

    @Test
    void 디코딩할_수_없으면_거부한다() {
        // content-type 헤더는 클라이언트가 마음대로 보낼 수 있지만
        // 실제로 디코딩되는지는 속일 수 없다 — 이게 위장 파일의 실질 관문이다.
        byte[] notAnImage = "MZ\0\0this is not an image".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> processor.process(notAnImage))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> processor.process(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
