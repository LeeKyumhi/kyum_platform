package com.guidematch.knowledge;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 업로드 이미지를 저장 가능한 형태로 바꾼다. DB·네트워크를 만지지 않는 순수 변환이라
 * 테스트가 쉽고, 실패가 업로드 트랜잭션 밖에서 끝난다.
 *
 * <p><b>세 가지를 동시에 해결한다.</b>
 * <ol>
 *   <li><b>EXIF 제거</b> — 폰 사진에는 GPS·촬영시각·기기명이 박혀 있다. 사용자는 대체로 모르고,
 *       공개 URL로 올라가면 촬영 이력이 그대로 노출된다. 재인코딩하면 통째로 사라진다.</li>
 *   <li><b>Orientation 반영</b> — 그런데 EXIF를 지우면 회전 정보도 같이 사라져 아이폰 사진이
 *       옆으로 눕는다. Thumbnailator는 Orientation을 <b>먼저 읽어 회전을 적용한 뒤</b> 쓴다.
 *       이것이 JDK 내장 ImageIO만으로 안 되는 이유이자 이 의존성을 넣은 이유다.</li>
 *   <li><b>2크기 생성</b> — 목록에 1600px을 15장 깔면 3~6MB라 오히려 느려진다. 목록은 thumb를 쓴다.</li>
 * </ol>
 */
@Component
public class PlaceImageProcessor {

    public static final int FULL_MAX_PX = 1600;
    public static final int THUMB_MAX_PX = 400;
    public static final double QUALITY = 0.82;

    /** 저장할 두 가지 바이트. 항상 JPEG이다 — 입력이 PNG여도 출력 포맷을 통일한다. */
    public record Processed(byte[] full, byte[] thumb) {}

    public Processed process(byte[] raw) {
        if (raw == null || raw.length == 0) {
            throw new IllegalArgumentException("빈 파일은 이미지가 아니다");
        }
        return new Processed(scale(raw, FULL_MAX_PX), scale(raw, THUMB_MAX_PX));
    }

    private byte[] scale(byte[] raw, int maxPx) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // Thumbnailator의 .size()는 원본이 더 작아도 확대해버린다(0.4.20 실측).
            // 긴 변 기준 배율을 직접 계산하고 1.0으로 상한을 걸어 확대를 막는다.
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
            if (src == null) {
                throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
            }
            int longest = Math.max(src.getWidth(), src.getHeight());
            double factor = Math.min(1.0, (double) maxPx / longest);

            Thumbnails.of(new ByteArrayInputStream(raw))
                    .scale(factor)
                    .useExifOrientation(true)   // ★ 눕는 사진을 막는 유일한 스위치
                    .outputFormat("jpg")
                    .outputQuality(QUALITY)
                    .toOutputStream(out);
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            // Thumbnailator는 디코딩 불가를 IOException("Could not obtain image from stream")으로 던진다.
            // 여기서 400으로 바꿔주지 않으면 위장 파일이 500이 되어 서버 오류로 보인다.
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다.", e);
        }
        if (out.size() == 0) {
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        }
        return out.toByteArray();
    }
}
