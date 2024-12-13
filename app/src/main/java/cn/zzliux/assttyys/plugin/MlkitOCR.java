package cn.zzliux.assttyys.plugin;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.internal.CommonComponentRegistrar;
import com.google.mlkit.common.sdkinternal.MlKitContext;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.common.internal.VisionCommonRegistrar;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.internal.TextRegistrar;

import java.util.*;

public class MlkitOCR {

    private final TextRecognizer client;
    private static boolean initialized = false;

    private static synchronized void initialize(Context context) {
        synchronized (MlkitOCR.class) {
            if (initialized) {
                return;
            }
            MlKitContext.initialize(context, Arrays.asList(new CommonComponentRegistrar(), new TextRegistrar(), new VisionCommonRegistrar()));
            initialized = true;
        }
    }

    public MlkitOCR(Context selfContext) {
        initialize(selfContext);
//        selfContext = (Context) runtime.getClass().getMethod("createScopedAppContext", Context.class, Context.class).invoke(runtime, context, selfContext);
        this.client = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    }


    public void detect(Bitmap bmp, ResultCallback resultCallback) {
        Log.d("MlkitOCRPlugin bmp", bmp.toString());
        Log.d("MlkitOCRPlugin rback", resultCallback.toString());
        Task<Text> pcs = this.client.process(InputImage.fromBitmap(bmp, 0));

        Objects.requireNonNull(resultCallback);
        pcs.addOnSuccessListener(new OnSuccessListener<Text>() {
            @Override
            public void onSuccess(Text text) {
                List<Text.Element> arrayList = new ArrayList<Text.Element>();
                for (Text.TextBlock textBlock : text.getTextBlocks()) {
                    for (Text.Line line : textBlock.getLines()) {
                        arrayList.addAll(line.getElements());
                    }
                }
                resultCallback.onSuccess(arrayList);
            }
        });
        pcs.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                resultCallback.onError(e);
            }
        });
    }

    public interface ResultCallback {
        void onError(Exception e);

        void onSuccess(List<Text.Element> list);
    }
}


