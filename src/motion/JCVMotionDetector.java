package motion;
// JCVMotionDetector.java
// Andrew Davison, ad@fivedots.coe.psu.ac.th, July 2013

/* Motion detections with JavaCV (http://code.google.com/p/javacv/).
 Compare the current image with the previous one to find the differences,
 then calculate the center-of-gravity (COG) of the difference image.

 Based on my CVMotionDetector class in the test-rig\ directory, 
 but now smoothing is used to calculate the returned COG point.
 */
import java.awt.*;
import java.awt.Point;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;

public class JCVMotionDetector {

    private static final int MIN_PIXELS = 100;
    // minimum number of non-black pixels needed for COG calculation
    private static final int LOW_THRESHOLD = 64;

    private static final int MAX_PTS = 5;

    private IplImage prevImg, currImg, diffImg;     // grayscale images (diffImg is bi-level)
    private Dimension imDim = null;    // image dimensions

    private Point[] cogPoints;   // array for smoothing COG points
    private int ptIdx, totalPts;

    public JCVMotionDetector(IplImage firstFrame) {
        if (firstFrame == null) {
            System.out.println("No frame to initialize motion detector");
            System.exit(1);
        }

        System.out.println("Initializing OpenCV motion detector...");
        imDim = new Dimension(firstFrame.width(), firstFrame.height());

        cogPoints = new Point[MAX_PTS];
        ptIdx = 0;
        totalPts = 0;

        prevImg = convertFrame(firstFrame);
        currImg = null;
        diffImg = IplImage.create(prevImg.width(), prevImg.height(), IPL_DEPTH_8U, 1);
    }  // end of JCVMotionDetector()

    public void calcMove(IplImage currFrame) // use a new image to create a new COG point
    {
        if (currFrame == null) {
            System.out.println("Current frame is null");
            return;
        }

        if (currImg != null) // store old current as the previous image
        {
            prevImg = currImg;
        }

        currImg = convertFrame(currFrame);

        cvAbsDiff(currImg, prevImg, diffImg);
           // calculate absolute difference between curr & previous images;
        // large value means movement; small value means no movement

        /* threshold to convert grayscale --> two-level binary:
         small diffs (0 -- LOW_THRESHOLD) --> 0
         large diffs (LOW_THRESHOLD+1 -- 255) --> 255   */
        cvThreshold(diffImg, diffImg, LOW_THRESHOLD, 255, CV_THRESH_BINARY);

        Point cogPoint = findCOG(diffImg);
        if (cogPoint != null) {    // store in points array
            cogPoints[ptIdx] = cogPoint;
            ptIdx = (ptIdx + 1) % MAX_PTS;   // the index cycles around the array
            if (totalPts < MAX_PTS) {
                totalPts++;
            }
        }
    }  // end of calcMove()

    public IplImage getCurrImg() {
        return currImg;
    }

    public IplImage getDiffImg() {
        return diffImg;
    }

    public Dimension getSize() {
        return imDim;
    }

    private IplImage convertFrame(IplImage img) /* Conversion involves: blurring, converting color to grayscale, and equalization */ {
    // blur image to get reduce camera noise 
        cvSmooth(img, img, CV_GAUSSIAN, 3, 0, 0, 0); 

        // convert to grayscale
        IplImage grayImg = IplImage.create(img.width(), img.height(), IPL_DEPTH_8U, 1);
        cvCvtColor(img, grayImg, CV_BGR2GRAY);

        cvEqualizeHist(grayImg, grayImg);       // spread out the grayscale range

        return grayImg;
    }  // end of convertFrame()

    private Point findCOG(IplImage diffImg) /*  If there are enough non-black pixels in the difference image
     (non-black means a difference, i.e. movement), then calculate the moments,
     and use them to calculate the (x,y) center of the white areas.
     These values are returned as a Point object. */ {
        Point pt = null;

        int numPixels = cvCountNonZero(diffImg);   // non-zero (non-black) means motion
        if (numPixels > MIN_PIXELS) {
            CvMoments moments = new CvMoments();
            cvMoments(diffImg, moments, 1);    // 1 == treat image as binary (0,255) --> (0,1)
            double m00 = cvGetSpatialMoment(moments, 0, 0);
            double m10 = cvGetSpatialMoment(moments, 1, 0);
            double m01 = cvGetSpatialMoment(moments, 0, 1);

            if (m00 != 0) {   // create COG Point
                int xCenter = (int) Math.round(m10 / m00);
                int yCenter = (int) Math.round(m01 / m00);
                pt = new Point(xCenter, yCenter);
            }
        }
        return pt;
    }  // end of findCOG()

    public Point getCOG() /* return average of points stored in cogPoints[], 
     to smooth the position */ {
        if (totalPts == 0) {
            return null;
        }

        int xTot = 0;
        int yTot = 0;
        for (int i = 0; i < totalPts; i++) {
            xTot += cogPoints[i].x;
            yTot += cogPoints[i].y;
        }

        return new Point((int) (xTot / totalPts), (int) (yTot / totalPts));
    }  // end of getCOG()

}  // end of JCVMotionDetector class
