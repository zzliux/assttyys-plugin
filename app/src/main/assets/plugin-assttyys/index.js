
module.exports = function(plugin){
    const runtime = plugin.runtime;
    const scope = plugin.topLevelScope;
    let OCRResult = $ocr.OCRResult;

    function Assttyys() {
    }
    let ocr = plugin.getOCR();
    Assttyys.ocr = {
        detect: function detectByOcr (image) {
            let input = image.getBitmap();
            let disposable = $threads.disposable();
            ocr.detect(input, {
//                    onSuccess: function (result) {
//                        console.log(toJsOCRResult(result));
//                    },
//                    onError: function (error) {
//                        throw new Error(error)
//                    }
                onSuccess: (text) => disposable.setAndNotify({ value: text }),
                onError: (error) => disposable.setAndNotify({ error: error }),
            });
            let result = disposable.blockedGet();
            if (result.error) {
                throw result.error;
            }
            return toJsOCRResult(result.value);
        }
    }

    function toJsOCRResult(elements) {
        let size = elements.size();
        let array = [];
        for (let i = 0; i < size; i++) {
            let element = elements.get(i);
            array.push(new OCRResult(element, {
                confidence: element.getConfidence(),
                text: element.getText(),
                bounds: element.getBoundingBox(),
                rotation: 0,
                rotationConfidence: 0,
            }));
        }
        return array;
    }

    // 光流计算场景位移（不好用，算出来的总是错的）
    let opticalFLow = plugin.getOpticalFlow();
    Assttyys.OpticalFlow = {
        detect: function (bitmap1, bitmap2) {
            return opticalFLow.runDenseOpticalFlowAndGet(bitmap1, bitmap2);
        }
    }

    // 场景位移计算（orb+模板匹配）
    let sceneMontion = plugin.getSceneMotion();
    Assttyys.SceneMotion = {
        clearExcludeRegions: function () {
            sceneMontion.clearExcludeRegions();
        },
        addExcludeRegion: function (x1, x2, y1, y2) {
            sceneMontion.addExcludeRegion(new android.graphics.Rect(x1, x2, y1, y2));
        },
        setInitialFrame: function (bitmap) {
            sceneMontion.setInitialFrame(bitmap);
        },
        detect: function (bitmap) {
            return sceneMontion.calculateMotion(bitmap);
        }
    }

    return Assttyys;
}