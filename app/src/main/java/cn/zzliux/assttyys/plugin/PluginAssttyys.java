package cn.zzliux.assttyys.plugin;

import android.content.Context;
import org.autojs.plugin.sdk.Plugin;

public class PluginAssttyys extends Plugin {
    private MlkitOCR mMlkitOCR;

    private OpticalFlow mOpticalFlow;

    private SceneMotion mSceneMotion;

    private final Context selfContext;


    public PluginAssttyys(Context context, Context selfContext, Object runtime, Object topLevelScope) {
        super(context, selfContext, runtime, topLevelScope);
        this.selfContext = selfContext;
    }

    @Override
    public String getAssetsScriptDir() {
        return "plugin-assttyys";
    }

    public MlkitOCR getOCR() {
        if (mMlkitOCR == null) {
            mMlkitOCR = new MlkitOCR(selfContext);
        }
        return mMlkitOCR;
    }

    public OpticalFlow getOpticalFlow() {
        if (mOpticalFlow == null) {
            mOpticalFlow = new OpticalFlow(selfContext);
        }
        return mOpticalFlow;
    }

    public SceneMotion getSceneMotion() {
        if (mSceneMotion == null) {
            mSceneMotion = new SceneMotion();
        }
        return mSceneMotion;
    }
}
