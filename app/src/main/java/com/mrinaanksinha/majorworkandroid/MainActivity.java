package com.mrinaanksinha.majorworkandroid;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

//TODO: ensure that if permissions not present, appropriate screen shown after permissions entered
public class MainActivity extends AppCompatActivity
{

    public Mat img;
    private final String TESS_DATA_PATH = "/tessdata";
    private TessBaseAPI tessBaseAPI;
    private FocusBox focusBox;
    private static ProgressBar progressBar;
    private TextView resultsTextView;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this)
    {
        @Override
        public void onManagerConnected(int status)
        {
            switch (status)
            {
                case LoaderCallbackInterface.SUCCESS:
                {
                    img = new Mat();

                }
                break;
                default:
                {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private Camera.PictureCallback pictureCallback = new Camera.PictureCallback()
    {
        @Override
        public void onPictureTaken(byte[] data, Camera camera)
        {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.bringToFront();
            captureButton.setEnabled(false);
            focusBox.enabled = false;
            File pictureFile = getOutputMediaFile();
            long x = pictureFile.length();
            if (pictureFile == null)
            {
                Log.d("TAG", "Error creating media file, check storage permissions: ");
                return;
            }

            try
            {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            }
            catch (FileNotFoundException e)
            {
                Log.d("TAG", "File not found: " + e.getMessage());
            }
            catch (IOException e)
            {
                Log.d("TAG", "Error accessing file: " + e.getMessage());
            }

            Bitmap bmp = ImageProcessingTools.getCroppedBitmap(pictureFile.getAbsolutePath(), preview.getWidth(), preview.getHeight(), focusBox.getSelectorViewBox());


            ExtractText extractText = new ExtractText();
            extractText.delegate = new AsyncResponse()
            {
                @Override
                public void processFinish(String result)
                {
                    progressBar.setVisibility(View.INVISIBLE);
                    captureButton.setEnabled(true);
                    focusBox.enabled = true;
                    resultsTextView.setText(result);
                }
            };

            extractText.execute(bmp);

            Log.d("TAG", "IMAGE LOADED");
        }
    };

    static Button captureButton;
    private static final int REQUEST_PERMISSIONS = 200;

    private ImageView selectorView;
    private Camera camera;
    private CameraPreview preview;
    private FrameLayout previewFrame;

    public static Button getCaptureButton()
    {
        return captureButton;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {

        if (grantResults[0] != PackageManager.PERMISSION_GRANTED)
        {


            if (requestCode == REQUEST_PERMISSIONS)
            {
                Toast.makeText(this, getString(R.string.CameraPermissionRequired), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {

        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera);
        if (!OpenCVLoader.initDebug())
        {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for Initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        }
        else
        {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSIONS);
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
        }

        prepareTessData();
        selectorView = (ImageView) findViewById(R.id.selectorView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        focusBox = new FocusBox(getApplicationContext(), selectorView);
        camera = getCameraInstance(getApplicationContext());

        preview = new CameraPreview(this, camera, this);
        preview.initFocus(ImageProcessingTools.getBox(selectorView));
        previewFrame = (FrameLayout) findViewById(R.id.cameraPreviewFrame);
        previewFrame.addView(preview);
        previewFrame.addView(focusBox);

        resultsTextView = (TextView) findViewById(R.id.resultsTextView);

//        textureView = (TextureView) findViewById(R.id.textureview);
//        textureView.setSurfaceTextureListener(surfaceTextureListener);
        captureButton = (Button) findViewById(R.id.captureButton);
        captureButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
//                getPicture();
                camera.takePicture(null, null, pictureCallback);
            }
        });


    }

    @Override
    protected void onPause()
    {
        super.onPause();
        releaseCamera();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (camera == null)
        {
            camera = getCameraInstance(getApplicationContext());
        }
        if (preview == null)
        {
            preview = new CameraPreview(this, camera, this);
            preview.initFocus(focusBox.getSelectorViewBox());
            previewFrame = (FrameLayout) findViewById(R.id.cameraPreviewFrame);
            previewFrame.addView(preview);
            focusBox.bringToFront();
        }
    }

    private void releaseCamera()
    {
        if (preview != null)
        {
            preview.removeFocusCallback();
            previewFrame.removeView(preview);
            preview = null;
        }
        if (camera != null)
        {
            camera.release();
            camera = null;
        }
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    public static Camera getCameraInstance(Context context)
    {
        Camera cam = null;
        try
        {
            cam = Camera.open(); // attempt to get a Camera instance
        }
        catch (RuntimeException e)
        {
            // Camera is not available (in use or does not exist)
            Toast.makeText(context, R.string.CameraInUse, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        Log.e(">>>>>>>", cam == null ? "cam = null" : "cam =/= null");
        return cam; // returns null if camera is unavailable
    }

    private File getOutputMediaFile()
    {
        File mediaStorageDir = new File(getFilesDir(), getString(R.string.app_name));

        if (!mediaStorageDir.exists())
        {
            if (!mediaStorageDir.mkdirs())
            {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;

        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "IMG_" + timeStamp + ".jpg");


        return mediaFile;
    }

    private void prepareTessData()
    {

        try
        {
            File dir = new File(getFilesDir().toString() + TESS_DATA_PATH);
            if (!dir.exists())
            {
                dir.mkdir();
            }
            String fileList[] = getAssets().list("");
            for (String fileName : fileList)
            {
                String pathToDataFile = getFilesDir() + TESS_DATA_PATH + "/" + fileName;
                if (!(new File(pathToDataFile)).exists())
                {
                    InputStream is = getAssets().open(fileName);
                    OutputStream os = new FileOutputStream(pathToDataFile);
                    byte[] buff = new byte[1024];
                    int len;
                    while ((len = is.read(buff)) > 0)
                    {
                        os.write(buff, 0, len);
                    }
                    is.close();
                    os.close();
                }
            }
        }

        catch (IOException e)
        {
            e.printStackTrace();

        }

        try
        {
            tessBaseAPI = new TessBaseAPI();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }


        tessBaseAPI.setPageSegMode(TessBaseAPI.OEM_TESSERACT_ONLY);//Change to Tesseract_cube_maybe Slower but more accurate
        tessBaseAPI.setDebug(true);

    }

    Handler extractTextHandler = new Handler(Looper.getMainLooper())
    {
        @Override
        public void handleMessage(Message message)
        {
            Toast.makeText(getApplicationContext(), (String) message.obj, Toast.LENGTH_SHORT).show();
        }
    };

    public static ProgressBar getProgressBar()
    {
        return progressBar;
    }


    private class ExtractText extends AsyncTask<Bitmap, Void, String>
    {

        public AsyncResponse delegate = null;

        @Override
        protected String doInBackground(Bitmap... bitmaps)
        {
            Bitmap bitmap = bitmaps[0];
            bitmap = preprocess(bitmap);
            if (bitmap == null)
            {
                return null;
            }
            String detectedTextBoxes = detectText(bitmap);
            String infixEquation = EquationTools.standardizeEquationToInfix(detectedTextBoxes, new android.util.Size(bitmap.getWidth(), bitmap.getHeight()));
            if (infixEquation == null || infixEquation.isEmpty())
            {
                Message message = extractTextHandler.obtainMessage(0, getString(R.string.EquationIsNullError));
                message.sendToTarget();
                return null;
            }
            ArrayList<String> postfixEquation = EquationTools.infixToPostfix(infixEquation);
            String equationSolution = EquationTools.solvePostfix(postfixEquation);
            String formattedEquationWSolution = infixEquation + " = " + equationSolution;

            return formattedEquationWSolution;
        }

        private Bitmap preprocess(Bitmap bitmap)
        {
            //        BitmapFactory.Options options = new BitmapFactory.Options();
//        options.inSampleSize =1;
//        bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.example1, options);
//        bitmap = ImageProcessingTools.rotateBitmap(bitmap,90);

            Utils.bitmapToMat(bitmap, img);

            Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(img, img, new Size(3, 3), 0);
            Imgproc.adaptiveThreshold(img, img, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 55, 10);
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(2, 2));
            Imgproc.morphologyEx(img, img, Imgproc.MORPH_ERODE, kernel);
            Mat croppedImg = rotateAndCrop(img);
            if (croppedImg == null)
            {
                Message message = extractTextHandler.obtainMessage(0, getString(R.string.BadLightingError));
                message.sendToTarget();
                return null;
            }
            bitmap = Bitmap.createBitmap(croppedImg.cols(), croppedImg.rows(), Bitmap.Config.ARGB_8888);

//        }

            Utils.matToBitmap(croppedImg, bitmap);
            return bitmap;
        }

        private Mat rotateAndCrop(@NonNull Mat src)
        {
            RotatedRect rect;
            Mat points = Mat.zeros(src.size(), src.channels());
            Core.findNonZero(src, points);

            MatOfPoint mpoints = new MatOfPoint(points);
            MatOfPoint2f points2f = new MatOfPoint2f(mpoints.toArray()); //TAKES WAY TOO LONGGGGG!!!!!!!

            if (points2f.rows() > 0)
            {
                rect = Imgproc.minAreaRect(points2f);
                double angle = rect.angle;
                Size croppedSize;
                if (rect.angle < -45.0)
                {
                    angle += 90;
                    croppedSize = new Size(rect.size.height, rect.size.width);
                }
                else
                {
                    croppedSize = rect.size;
                }

                Mat rotMat = Imgproc.getRotationMatrix2D(rect.center, angle, 1);
                Mat rotated = new Mat();
                Imgproc.warpAffine(src, rotated, rotMat, new Size(src.width(), src.height()), Imgproc.INTER_CUBIC);
                Mat cropped = new Mat();
                Imgproc.getRectSubPix(rotated, croppedSize, rect.center, cropped);
                return cropped;
            }
            else
            {
                return null;
            }
        }


        private String detectText(Bitmap bitmap)
        {
            try
            {
                tessBaseAPI.init(getFilesDir().toString(), "eng");
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            tessBaseAPI.setImage(bitmap);
            tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789+=()-/x*");
//        String extractedText = tessBaseAPI.getUTF8Text();
            String extractedBoxText = tessBaseAPI.getBoxText(0);

            tessBaseAPI.clear();
            tessBaseAPI.end();
            return extractedBoxText;

        }

        @Override
        protected void onPostExecute(String result)
        {
            delegate.processFinish(result);
        }
    }

}

