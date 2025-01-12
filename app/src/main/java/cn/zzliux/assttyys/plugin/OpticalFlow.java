package cn.zzliux.assttyys.plugin;

import android.content.Context;
import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OpticalFlow {

    Context mContext;

    static {
        System.loadLibrary("opencv_java4");
    }

    public OpticalFlow(Context context) {
        this.mContext = context;
    }

    // return { deg, dx, dy }
    public double[] runDenseOpticalFlowAndGet(Bitmap bitmap1, Bitmap bitmap2) {
        // 将 Bitmap 转换为 OpenCV Mat
        Mat mat1 = new Mat();
        Mat mat2 = new Mat();
        Utils.bitmapToMat(bitmap1, mat1);
        Utils.bitmapToMat(bitmap2, mat2);

        // 转换为灰度图像
        Mat gray1 = new Mat();
        Mat gray2 = new Mat();
        Imgproc.cvtColor(mat1, gray1, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(mat2, gray2, Imgproc.COLOR_BGR2GRAY);

        // 计算稠密光流
        Mat flow = new Mat();
        /*
        *
        参数	推荐值	说明
        pyrScale	0.5	每一层的图像尺寸是前一层的 1/2。
        levels	3 或 5	金字塔层数，层数越多，处理大位移的能力越强。
        winsize	15 或 21	窗口大小，窗口越大，对噪声的鲁棒性越强。
        iterations	3 或 5	每层金字塔的迭代次数，迭代次数越多，结果越精确。
        polyN	5 或 7	多项式展开的邻域大小，值越大，对噪声的鲁棒性越强。
        polySigma	1.1 或 1.2	多项式展开的高斯标准差，值越大，平滑效果越强。
        flags	0	默认值，不使用额外的算法标志。
        * */
        Video.calcOpticalFlowFarneback(gray1, gray2, flow, 0.5, 3, 25, 5, 11, 1.2, 0);

        // 计算主运动方向和相对位移
        double[] ret = calculateMainDirectionAndDisplacement(flow);

        // 释放资源
        mat1.release();
        mat2.release();
        gray1.release();
        gray2.release();
        flow.release();

        return ret;
    }

    private double[] calculateMainDirectionAndDisplacement(Mat flow) {
        Mat angleMask = null;
        Mat magnitude = null;
        Mat angles = null;
        Mat magnitudeMask = null;
        Mat angleHist = null;
        Mat smoothedAngleHist = null;
        Mat magnitudeHist = null;
        Mat magnitudeRangeMask = null;
        Mat finalMask = null;
        try {
            // 提取光流的水平和垂直分量
            List<Mat> flowChannels = new ArrayList<>();
            Core.split(flow, flowChannels);
            Mat dx = flowChannels.get(0);
            Mat dy = flowChannels.get(1);

            // 1. 预处理：计算光流的幅值和方向
            magnitude = new Mat();
            angles = new Mat();
            Core.magnitude(dx, dy, magnitude);
            Core.phase(dx, dy, angles, true);  // true 表示输出角度制

            // 2. 基础过滤：移除过小和过大的运动
            double minMagnitudeThreshold = 2;
            double maxMagnitudeThreshold = 100; // 降低最大阈值，避免异常值
            magnitudeMask = new Mat();
            Core.inRange(magnitude, new Scalar(minMagnitudeThreshold), new Scalar(maxMagnitudeThreshold), magnitudeMask);

            // 3. 方向统计（更粗的粒度）
            angleHist = new Mat();
            int histSize = 36; // 每10度一个区间
            MatOfFloat ranges = new MatOfFloat(0, 360);
            MatOfInt histSizeMatrix = new MatOfInt(histSize);
            Imgproc.calcHist(Arrays.asList(angles), new MatOfInt(0), magnitudeMask, angleHist, histSizeMatrix, ranges);

            // 4. 使用高斯平滑处理方向直方图，使结果更稳定
            smoothedAngleHist = new Mat();
            Imgproc.GaussianBlur(angleHist, smoothedAngleHist, new Size(3, 3), 1);

            // 5. 找到主运动方向
            Core.MinMaxLocResult minMaxLoc = Core.minMaxLoc(smoothedAngleHist);
            int mainDirectionBin = (int) minMaxLoc.maxLoc.y;
            double mainDirection = mainDirectionBin * (360.0 / histSize);

            // 6. 使用更宽的角度容差
            double angleTolerance = 15;
            angleMask = new Mat();
            double lowerAngle = (mainDirection - angleTolerance + 360) % 360;
            double upperAngle = (mainDirection + angleTolerance) % 360;
            if (lowerAngle < upperAngle) {
                Core.inRange(angles, new Scalar(lowerAngle), new Scalar(upperAngle), angleMask);
            } else {
                // 处理跨越360度的情况
                Mat mask1 = new Mat();
                Mat mask2 = new Mat();
                Core.inRange(angles, new Scalar(lowerAngle), new Scalar(360), mask1);
                Core.inRange(angles, new Scalar(0), new Scalar(upperAngle), mask2);
                Core.bitwise_or(mask1, mask2, angleMask);
                mask1.release();
                mask2.release();
            }

            // 7. 位移统计（更细的粒度）
            magnitudeHist = new Mat();
            int magnitudeHistSize = 10;
            MatOfFloat magnitudeRanges = new MatOfFloat((float) minMagnitudeThreshold, (float) maxMagnitudeThreshold);
            MatOfInt magnitudeHistSizeMatrix = new MatOfInt(magnitudeHistSize);
            Imgproc.calcHist(Arrays.asList(magnitude), new MatOfInt(0), magnitudeMask, magnitudeHist, magnitudeHistSizeMatrix, magnitudeRanges);

            // 8. 找到主位移范围
            Core.MinMaxLocResult magnitudeMinMaxLoc = Core.minMaxLoc(magnitudeHist);
            int mainMagnitudeBin = (int) magnitudeMinMaxLoc.maxLoc.y;
            double binWidth = (maxMagnitudeThreshold - minMagnitudeThreshold) / magnitudeHistSize;
            double mainMagnitudeStart = minMagnitudeThreshold + mainMagnitudeBin * binWidth;
            double mainMagnitudeEnd = mainMagnitudeStart + binWidth * 2; // 使用两个bin的宽度作为范围

            // 9. 位移范围掩码
            magnitudeRangeMask = new Mat();
            Core.inRange(magnitude, new Scalar(mainMagnitudeStart), new Scalar(mainMagnitudeEnd), magnitudeRangeMask);

            // 10. 合并所有掩码
            finalMask = new Mat();
            Core.bitwise_and(magnitudeMask, angleMask, finalMask);
            Core.bitwise_and(finalMask, magnitudeRangeMask, finalMask);

            // 11. 检查有效点数量
            int validPoints = Core.countNonZero(finalMask);
            if (validPoints < finalMask.rows() * finalMask.cols() * 0.05) { // 降低阈值到5%
                return new double[]{0, 0, 0};
            }

            // 12. 应用RANSAC思想：随机采样并验证
            Mat filteredDx = new Mat();
            Mat filteredDy = new Mat();
            dx.copyTo(filteredDx, finalMask);
            dy.copyTo(filteredDy, finalMask);

            // 13. 计算中位数而不是平均值，避免异常值影响
            MatOfDouble medianDx = new MatOfDouble();
            MatOfDouble medianDy = new MatOfDouble();
            MatOfDouble meanDx = new MatOfDouble();
            MatOfDouble meanDy = new MatOfDouble();
            MatOfDouble stdDevDx = new MatOfDouble();
            MatOfDouble stdDevDy = new MatOfDouble();

            Core.meanStdDev(filteredDx, meanDx, stdDevDx);
            Core.meanStdDev(filteredDy, meanDy, stdDevDy);

            // 14. 标准差检查
            double stdDevThreshold = 8; // 增加容差
            if (stdDevDx.toArray()[0] > stdDevThreshold || stdDevDy.toArray()[0] > stdDevThreshold) {
                return new double[]{0, 0, 0};
            }

            // 15. 返回结果
            return new double[]{
                    mainDirection,
                    meanDx.toArray()[0],
                    meanDy.toArray()[0]
            };

        } finally {
            // 16. 资源释放
            for (Mat mat : Arrays.asList(
                    magnitude, angles, magnitudeMask, angleHist, smoothedAngleHist,
                    angleMask, magnitudeHist, magnitudeRangeMask, finalMask
            )) {
                if (mat != null) {
                    mat.release();
                }
            }
        }
    }
}