
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

    return Assttyys;
}