package com.surcumference.fingerprintlocation.diagnostic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Base64;
import android.view.View;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

final class QrExport extends View {
    private final BitMatrix matrix;
    private final Paint paint = new Paint();
    private Runnable afterDraw;

    QrExport(Context context, String json) throws IOException, WriterException {
        super(context);
        String payload = payload(json);
        Map<EncodeHintType,Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.MARGIN,4);
        matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE,0,0,hints);
        paint.setColor(Color.BLACK);
        setContentDescription("Offline fingerprint diagnostic JSON QR");
    }
    static String payload(String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65536) throw new IOException("Diagnostic too large for QR");
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {gzip.write(bytes);}
        return "FPSL1:" + Base64.encodeToString(compressed.toByteArray(), Base64.NO_WRAP);
    }
    void afterDraw(Runnable callback) {afterDraw = callback;}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);canvas.drawColor(Color.WHITE);
        int scale = Math.min(getWidth(),getHeight()) / matrix.getWidth();
        if (scale < 1) return;
        int x0 = (getWidth()-matrix.getWidth()*scale)/2;
        int y0 = (getHeight()-matrix.getHeight()*scale)/2;
        for (int y=0;y<matrix.getHeight();y++) for (int x=0;x<matrix.getWidth();x++)
            if (matrix.get(x,y)) canvas.drawRect(x0+x*scale,y0+y*scale,x0+(x+1)*scale,y0+(y+1)*scale,paint);
        if (afterDraw != null) {
            Runnable callback = afterDraw;
            afterDraw = null;
            postOnAnimation(() -> postOnAnimation(callback));
        }
    }
}
