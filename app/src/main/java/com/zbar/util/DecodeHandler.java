
package com.zbar.util;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import com.zbar.camera.R;
import com.zbar.view.ViewfinderView;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public final class DecodeHandler extends Handler {

    private static final String TAG = "DecodeHandler";
    private DecodeCallback callback;
    private ViewfinderView viewfinderView;
    private ImageScanner scanner;

    static {
        System.loadLibrary("iconv");
    }

    public interface DecodeCallback {
        void onDecodeResult(String result);
    }

    public DecodeHandler(Looper looper) {
        this(looper, null, null);
    }

    public DecodeHandler(Looper looper, DecodeCallback callback, ViewfinderView view) {
        super(looper);
        this.callback = callback;
        this.viewfinderView = view;
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);
    }


    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case R.id.decode:
                decode((byte[]) message.obj, message.arg1, message.arg2);
                break;
            case R.id.quit:
                Looper.myLooper().quit();
                break;
        }
    }

    private void decode(byte[] data, int width, int height) {

        Image barcode = new Image(width, height, "Y800");
        if (viewfinderView != null) {
            Rect scanImageRect = viewfinderView.getScanImageRect(height, width);
            barcode.setCrop(scanImageRect.top, scanImageRect.left, scanImageRect.bottom, scanImageRect.right);
        }
        barcode.setData(data);

        int result = scanner.scanImage(barcode);
        String strResult = "";
        if (result != 0) {
            SymbolSet syms = scanner.getResults();
            for (Symbol sym : syms) {
                strResult = sym.getData().trim();
                if (!strResult.isEmpty()) {
                    break;
                }
            }
        }

        if (this.callback != null) {
            this.callback.onDecodeResult(strResult);
        }
    }

}
