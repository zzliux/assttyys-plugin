package cn.zzliux.assttyys.plugin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.FastFeatureDetector;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class SceneMotion {
    private Context mContext;
    private static final int MIN_MATCHES = 6;  // 降低最小匹配点要求
    private static final double RANSAC_THRESHOLD = 5.0;  // 适当放宽RANSAC阈值
    private static final float RATIO_THRESH = 0.75f;  // 距离比率阈值

    private List<Rect> excludeRegions; // 排除区域列表
    private Mat lastFrame;  // 添加缓存上一帧的Mat对象
    private MatOfKeyPoint lastKeypoints;  // 缓存上一帧的特征点
    private Mat lastDescriptors;  // 缓存上一帧的描述子

    MatOfKeyPoint keypoints2;

    Mat descriptors2;

    private Mat excludeMask;  // 添加掩码成员变量

    static {
        System.loadLibrary("opencv_java4");
    }

    public SceneMotion(Context context) {
        this.mContext = context;
        this.excludeRegions = new ArrayList<>();
    }

    /**
     * 添加需要排除的特征点区域
     *
     * @param rect 要排除的区域
     */
    public void addExcludeRegion(Rect rect) {
        excludeRegions.add(rect);
        updateExcludeMask(lastFrame != null ? lastFrame.size() : new Size(0, 0));
    }

    /**
     * 清除所有排除区域
     */
    public void clearExcludeRegions() {
        excludeRegions.clear();
        if (excludeMask != null) {
            excludeMask.release();
            excludeMask = null;
        }
    }

    private void updateExcludeMask(Size size) {
        if (size.width == 0 || size.height == 0) {
            return;
        }
        
        if (excludeMask != null) {
            excludeMask.release();
        }
        
        // 创建全白色掩码（255表示有效区域）
        excludeMask = new Mat(size, CvType.CV_8UC1, new Scalar(255));
        
        // 在排除区域绘制黑色（0表示排除区域）
        for (Rect region : excludeRegions) {
            // 使用两个Point来定义矩形的左上角和右下角
            Point pt1 = new Point(region.left, region.top);
            Point pt2 = new Point(region.right, region.bottom);
            Imgproc.rectangle(excludeMask, pt1, pt2, new Scalar(0), -1);
        }
    }

    /**
     * 设置初始帧
     * @param bitmap 初始帧图片
     */
    public void setInitialFrame(Bitmap bitmap) {
        if (lastFrame != null) {
            releaseLastFrameData();
        }
        
        lastFrame = new Mat();
        Utils.bitmapToMat(bitmap, lastFrame);
        
        // 更新掩码大小
        updateExcludeMask(lastFrame.size());
        
        // 计算并缓存特征点和描述子
        Mat gray = new Mat();
        Imgproc.cvtColor(lastFrame, gray, Imgproc.COLOR_BGR2GRAY);
        
        try {
            lastKeypoints = new MatOfKeyPoint();
            lastDescriptors = new Mat();
            
            // 检测特征点
            FastFeatureDetector detector = FastFeatureDetector.create();
            detector.setThreshold(20);
            detector.setNonmaxSuppression(true);
            
            List<KeyPoint> kp1List = new ArrayList<>();
            
            // 使用掩码检测特征点
            detector.detect(gray, lastKeypoints, excludeMask);
            kp1List.addAll(lastKeypoints.toList());
            
            // 创建放大图并检测特征点
//            Mat grayScaled = new Mat();
//            Imgproc.resize(gray, grayScaled, new Size(), 1.5, 1.5, Imgproc.INTER_LINEAR);
//            MatOfKeyPoint keypointsScaled = new MatOfKeyPoint();
//            detector.detect(grayScaled, keypointsScaled);
            
            // 调整放大图上的特征点坐标
//            List<KeyPoint> scaledKp = keypointsScaled.toList();
//            for (KeyPoint kp : scaledKp) {
//                kp.pt.x /= 1.5;
//                kp.pt.y /= 1.5;
//                kp1List.add(kp);
//            }
            
            lastKeypoints.fromList(kp1List);
            
            // 计算描述子
            ORB orb = ORB.create();
            orb.setWTA_K(3);
            orb.setEdgeThreshold(31);
            orb.compute(gray, lastKeypoints, lastDescriptors);
            
//            grayScaled.release();
//            keypointsScaled.release();
        } finally {
            gray.release();
        }
    }

    private void releaseLastFrameData() {
        if (lastFrame != null) {
            lastFrame.release();
            lastFrame = null;
        }
        if (lastKeypoints != null) {
            lastKeypoints.release();
            lastKeypoints = null;
        }
        if (lastDescriptors != null) {
            lastDescriptors.release();
            lastDescriptors = null;
        }
    }

    /**
     * 计算当前帧与上一帧之间的位移
     * @param currentBitmap 当前帧图片
     * @return Double[]{angle, dx, dy} 角度和位移，匹配失败时返回null
     */
    public Double[] calculateMotion(Bitmap currentBitmap) {
        if (lastFrame == null) {
            setInitialFrame(currentBitmap);
            return new Double[]{0.0, 0.0, 0.0};
        }

        Mat currentFrame = new Mat();
        Utils.bitmapToMat(currentBitmap, currentFrame);

        try {
            Double[] result = calculateSceneMotion(currentFrame);
            if (result != null) {
                // 更新lastFrame
                releaseLastFrameData();
                lastFrame = currentFrame.clone();
                
                // 直接从calculateSceneMotion中获取新计算的特征点和描述子
                // 需要修改calculateSceneMotion方法，让它返回这些信息
                // 或者将keypoints2和descriptors2设为类成员变量
                lastKeypoints = keypoints2;  // 需要从calculateSceneMotion中获取
                lastDescriptors = descriptors2;  // 需要从calculateSceneMotion中获取
                
                return result;
            }
            return null;
        } finally {
            currentFrame.release();
        }
    }

    private Double[] calculateSceneMotion(Mat img2) {
        // 1. 转换为灰度图
        Mat gray2 = new Mat();
        Imgproc.cvtColor(img2, gray2, Imgproc.COLOR_BGR2GRAY);

        try {
            // 2. 使用FAST特征点检测器检测当前帧特征点
            FastFeatureDetector detector = FastFeatureDetector.create();
            detector.setThreshold(20);
            detector.setNonmaxSuppression(true);

            keypoints2 = new MatOfKeyPoint();
            List<KeyPoint> kp2List = new ArrayList<>();

            // 使用掩码检测特征点
            detector.detect(gray2, keypoints2, excludeMask);
            kp2List.addAll(keypoints2.toList());

            // 在放大图上检测特征点
//            Mat img2Scaled = new Mat();
//            Imgproc.resize(gray2, img2Scaled, new Size(), 1.5, 1.5, Imgproc.INTER_LINEAR);
//            MatOfKeyPoint keypointsScaled2 = new MatOfKeyPoint();
//            detector.detect(img2Scaled, keypointsScaled2);

//            List<KeyPoint> scaledKp2 = keypointsScaled2.toList();
//            for (KeyPoint kp : scaledKp2) {
//                kp.pt.x /= 1.5;
//                kp.pt.y /= 1.5;
//                kp2List.add(kp);
//            }

            keypoints2.fromList(kp2List);

            // 3. 计算当前帧描述子
            descriptors2 = new Mat();
            ORB orb = ORB.create();
            orb.setWTA_K(3);
            orb.setEdgeThreshold(31);
            orb.compute(gray2, keypoints2, descriptors2);

            // 4. 特征点匹配（使用缓存的上一帧特征点和描述子）
            if (lastDescriptors == null || descriptors2 == null || 
                lastDescriptors.empty() || descriptors2.empty()) {
                return null;
            }

            BFMatcher matcher = BFMatcher.create(Core.NORM_HAMMING, false);
            List<MatOfDMatch> knnMatches = new ArrayList<>();
            matcher.knnMatch(lastDescriptors, descriptors2, knnMatches, 2);

            // 应用比率测试
            List<DMatch> matchesList = new ArrayList<>();
            for (MatOfDMatch matches : knnMatches) {
                if (matches.rows() > 1) {
                    DMatch[] matchesArr = matches.toArray();
                    if (matchesArr[0].distance < RATIO_THRESH * matchesArr[1].distance) {
                        matchesList.add(matchesArr[0]);
                    }
                }
            }

            // 5. 过滤匹配点
            // if (matchesList.size() < MIN_MATCHES) {
            //     return null;
            // }

            List<Point> points1 = new ArrayList<>();
            List<Point> points2 = new ArrayList<>();

            KeyPoint[] keypoints1Array = lastKeypoints.toArray();
            KeyPoint[] keypoints2Array = keypoints2.toArray();

            for (DMatch match : matchesList) {
                points1.add(keypoints1Array[match.queryIdx].pt);
                points2.add(keypoints2Array[match.trainIdx].pt);
            }

            // 6. 使用RANSAC估计位移
            if (points1.size() < MIN_MATCHES) {
                return null;
            }

            MatOfPoint2f pts1 = new MatOfPoint2f();
            MatOfPoint2f pts2 = new MatOfPoint2f();
            pts1.fromList(points1);
            pts2.fromList(points2);

            Mat mask = new Mat();
            Mat H = Calib3d.findHomography(pts1, pts2, Calib3d.RANSAC, RANSAC_THRESHOLD, mask);

            if (H == null || H.empty()) {
                return null;
            }

            // 获取单应性矩阵后的计算部分需要修改
            double dx = -H.get(0, 2)[0];  // 取负值来得到实际的运动方向
            double dy = -H.get(1, 2)[0];  // 取负值来得到实际的运动方向
            double angle = -Math.atan2(H.get(1, 0)[0], H.get(0, 0)[0]) * 180 / Math.PI;  // 取负值来得到实际的旋转方向

            // 如果位移小于1像素，认为是静止的
            if (Math.abs(dx) < 1 && Math.abs(dy) < 1) {
                dx = 0;
                dy = 0;
            }

            return isValidMotion(dx, dy, angle) ?
                    new Double[]{angle, dx, dy} : null;

        } finally {
            gray2.release();
        }
    }

    private boolean isValidMotion(double dx, double dy, double angle) {
        // 检查运动是否在合理范围内
        double maxMotion = 100; // 最大允许位移
        return Math.abs(dx) <= maxMotion &&
                Math.abs(dy) <= maxMotion;
    }

    public void release() {
        releaseLastFrameData();
        if (excludeMask != null) {
            excludeMask.release();
            excludeMask = null;
        }
    }
}