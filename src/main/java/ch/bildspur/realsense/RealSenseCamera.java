package ch.bildspur.realsense;

import org.librealsense.*;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PImage;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.List;

/**
 * Intel RealSense Camera
 */
public class RealSenseCamera implements PConstants {
    // processing
    private PApplet parent;

    // realsense
    private Context context;
    private Pipeline pipeline;

    private final int depthStreamIndex = 0;
    private final int colorStreamIndex = 0;

    // camera
    volatile private boolean running = false;

    private int width;
    private int height;
    private int fps;
    private boolean enableDepthStream;
    private boolean enableColorStream;

    private PImage depthImage;
    private PImage colorImage;

    /**
     * Create a new Intel RealSense camera.
     * @param parent Parent processing sketch.
     */
    public RealSenseCamera(PApplet parent)
    {
        this.parent = parent;
        parent.registerMethod("stop", this);

        // load native libs
        loadNativeLibraries();

        // create context
        context = Context.create();
    }

    /**
     * Start the camera.
     * @param width Width of the camera image.
     * @param height Height of the camera image.
     * @param fps Frames per second to capture.
     * @param enableDepthStream True if depth stream should be enabled.
     * @param enableColorStream True if color stream should be enabled.
     */
    public void start(int width, int height, int fps, boolean enableDepthStream, boolean enableColorStream)
    {
        DeviceList deviceList = this.context.queryDevices();
        List<Device> devices = deviceList.getDevices();

        if(devices.isEmpty())
            PApplet.println("RealSense: No device found!");

        this.start(devices.get(0), width, height, fps, enableDepthStream, enableColorStream);
    }

    /**
     * Start the camera.
     * @param device Intel RealSense device to start.
     * @param width Width of the camera image.
     * @param height Height of the camera image.
     * @param fps Frames per second to capture.
     * @param enableDepthStream True if depth stream should be enabled.
     * @param enableColorStream True if color stream should be enabled.
     */
     public void start(Device device, int width, int height, int fps, boolean enableDepthStream, boolean enableColorStream)
     {
         if(running)
             return;

         // set configuration
         this.width = width;
         this.height = height;
         this.fps = fps;
         this.enableDepthStream = enableDepthStream;
         this.enableColorStream = enableColorStream;

         // create images
         this.depthImage = new PImage(this.width, this.height, PConstants.RGB);
         this.colorImage = new PImage(this.width, this.height, PConstants.RGB);

         // create pipeline
         pipeline = context.createPipeline();

         Config config = Config.create();
         config.enableDevice(device);
         if (this.enableDepthStream) {
             config.enableStream(Native.Stream.RS2_STREAM_DEPTH,
                     this.depthStreamIndex,
                     this.width,
                     this.height,
                     Native.Format.RS2_FORMAT_Z16,
                     this.fps);
         }

         if (this.enableColorStream) {
             config.enableStream(Native.Stream.RS2_STREAM_COLOR,
                     this.colorStreamIndex,
                     this.width,
                     this.height,
                     Native.Format.RS2_FORMAT_RGB8,
                     this.fps);
         }

         // start pipeline
         pipeline.startWithConfig(config);

         running = true;
     }

    public void readFrames()
    {
        FrameList frames = pipeline.waitForFrames(5000);

        for (int i = 0; i < frames.frameCount(); i++) {
            Frame frame = frames.frame(i);
            if (frame.isExtendableTo(Native.Extension.RS2_EXTENSION_DEPTH_FRAME)) {
                this.readDepthImage(frame);
            } else {
                this.readColorImage(frame);
            }

            frame.release();
        }

        frames.release();
    }

    /**
     * Returns true if a device is available.
     * @return True if device is available.
     */
    public boolean isCameraAvailable() {
        DeviceList deviceList = this.context.queryDevices();
        return deviceList.getDeviceCount() > 0;
    }

    /**
     * Stop the camera.
     */
    public void stop()
    {
        if(!running)
            return;

        // set states
        running = false;
    }

    private void readDepthImage(Frame frame) {
        CharBuffer buffer = frame.getFrameData().asCharBuffer();
        this.depthImage.loadPixels();

        for (int i = 0; i < width * height; i++)
        {
            int depth = buffer.get(i) & 0xFFFF;
            int grayScale = (int) PApplet.map(depth, 0, 65536, 255, 0);

            if (depth > 0)
                depthImage.pixels[i] = parent.color(grayScale);
            else
                depthImage.pixels[i] = parent.color(0);
        }

        this.depthImage.updatePixels();
    }

    private void readColorImage(Frame frame) {
        ByteBuffer buffer = frame.getFrameData();
        this.colorImage.loadPixels();

        for (int i = 0; i < frame.getStrideInBytes() * height; i += 3)
        {
            colorImage.pixels[i / 3] = parent.color(buffer.get(i) & 0xFF, buffer.get(i + 1) & 0xFF, buffer.get(i + 2) & 0xFF);
        }

        this.colorImage.updatePixels();
    }

    private void loadNativeLibraries()
    {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            Native.loadNativeLibraries("native/windows-x64");
        } else if (os.contains("mac")) {
            Native.loadNativeLibraries("native/osx-x64");
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            Native.loadNativeLibraries("native/linux-64");
        } else {
            // Operating System not supported!
            PApplet.println("RealSense: Load the native libraries by your own.");
        }
    }

    /**
     * Check if the camera is already running.
     * @return True if the camera is already running.
     */
    public boolean isRunning() {
        return running;
    }

    public PImage getDepthImage() {
        return depthImage;
    }

    public PImage getColorImage() {
        return colorImage;
    }
}