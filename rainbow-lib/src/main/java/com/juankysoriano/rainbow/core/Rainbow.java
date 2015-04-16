package com.juankysoriano.rainbow.core;

import android.content.Context;
import android.os.AsyncTask;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import com.juankysoriano.rainbow.core.drawing.RainbowDrawer;
import com.juankysoriano.rainbow.core.drawing.RainbowTextureView;
import com.juankysoriano.rainbow.core.event.RainbowInputController;
import com.juankysoriano.rainbow.core.graphics.RainbowGraphics;
import com.juankysoriano.rainbow.core.graphics.RainbowGraphics2D;

import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Rainbow implements PaintStepListener {
    private static final int DEFAULT_FRAME_RATE = 60;
    private float frameRate = DEFAULT_FRAME_RATE;
    private boolean surfaceReady;
    private int width;
    private int height;
    private int frameCount;
    private boolean stopped = true;
    private boolean started = false;
    private boolean paused = true;
    private boolean resumed = false;
    private boolean isSetup = false;
    private ScheduledExecutorService drawingScheduler;
    private RainbowInputController rainbowInputController;
    private DrawingTask drawingTask;
    private RainbowDrawer rainbowDrawer;
    private RainbowTextureView drawingView;

    protected Rainbow(ViewGroup viewGroup) {
        this(new RainbowDrawer(), new RainbowInputController());
        injectInto(viewGroup);
    }

    protected Rainbow(RainbowDrawer rainbowDrawer, RainbowInputController rainbowInputController) {
        this.rainbowInputController = rainbowInputController;
        this.rainbowDrawer = rainbowDrawer;
        this.drawingTask = new DrawingTask();
        rainbowInputController.setPaintStepListener(new PaintStepListener() {
            @Override
            public void onDrawingStep() {
                performDrawingStep();
            }
        });
    }

    protected Rainbow(ViewGroup viewGroup, RainbowDrawer rainbowDrawer, RainbowInputController rainbowInputController) {
        this(rainbowDrawer, rainbowInputController);
        injectInto(viewGroup);
    }

    public void injectInto(ViewGroup viewGroup) {
        this.drawingView = new RainbowTextureView(viewGroup, this);
        addOnPreDrawListener();
    }

    private void addOnPreDrawListener() {
        drawingView.getViewTreeObserver().addOnPreDrawListener(onPreDrawListener);
    }

    private ViewTreeObserver.OnPreDrawListener onPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            removeOnPreDrawListener();
            setupSketch();
            return true;
        }

        private void removeOnPreDrawListener() {
            drawingView.getViewTreeObserver().removeOnPreDrawListener(this);
        }
    };

    public Context getContext() {
        return drawingView.getContext();
    }

    private void setupSketch() {
        setupSketchTask.execute();
    }

    private AsyncTask<Void, Void, Void> setupSketchTask = new AsyncTask<Void, Void, Void>() {
        @Override
        protected void onPreExecute() {
            width = drawingView.getMeasuredWidth();
            height = drawingView.getMeasuredHeight();
            initGraphics(width, height);
        }

        private void initGraphics(int width, int height) {
            RainbowGraphics graphics = new RainbowGraphics2D();
            graphics.setParent(Rainbow.this);
            graphics.setPrimary(true);
            if (width > 0 && height > 0) {
                graphics.setSize(width, height);
                rainbowDrawer.setGraphics(graphics);
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            rainbowDrawer.beginDraw();
            Rainbow.this.onSketchSetup();
            rainbowDrawer.endDraw();
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            isSetup = true;
            surfaceReady = true;
        }
    };

    public void onSketchSetup() {
        //no-op
    }

    public void start() {
        if (!isStarted() || drawingScheduler.isTerminated()) {
            onDrawingStart();
            started = true;
            stopped = false;
            resume();
        }
    }

    private boolean isStarted() {
        return started;
    }

    public void onDrawingStart() {
        //no-op
    }

    public void resume() {
        if (!isResumed()) {
            onDrawingResume();
            resumed = true;
            paused = false;
            if (hasDrawingScheduler() || drawingScheduler.isTerminated()) {
                drawingScheduler = Executors.newSingleThreadScheduledExecutor();
                drawingScheduler.scheduleAtFixedRate(drawingTask, 0, drawingTask.getDelay(), TimeUnit.MILLISECONDS);
            }
        }
    }

    private boolean isResumed() {
        return resumed;
    }

    private boolean hasDrawingScheduler() {
        return drawingScheduler == null;
    }

    public void onDrawingResume() {
        //no-op
    }

    private void handleDraw() {
        if (canDraw()) {
            performDrawingStep();
        }
    }

    private boolean canDraw() {
        return rainbowDrawer != null
                && rainbowDrawer.hasGraphics()
                && surfaceReady
                && isSetup
                && !rainbowInputController.isScreenTouched();
    }

    private void performDrawingStep() {
        frameCount++;
        if (hasToPaintIntoBuffer()) {
            doDrawingIfNotTouching();
        } else {
            rainbowDrawer.beginDraw();
            doDrawingIfNotTouching();
            rainbowDrawer.endDraw();
        }
    }

    private void doDrawingIfNotTouching() {
        onDrawingStep();
    }

    private boolean hasToPaintIntoBuffer() {
        return frameCount % 3 != 0;
    }

    public void onDrawingStep() {
        //no-op
    }

    public void pause() {
        if (!isPaused()) {
            paused = true;
            resumed = false;
            onDrawingPause();
        }
    }

    private boolean isPaused() {
        return paused;
    }

    public void onDrawingPause() {
        //no-op
    }

    public void stop() {
        if (!isStopped()) {
            pause();
            shutDownExecutioner();
            onDrawingStop();
            stopped = true;
            started = false;
        }
    }

    private boolean isStopped() {
        return stopped;
    }

    public void onDrawingStop() {
        //no-op
    }

    public void destroy() {
        stop();
        onSketchDestroy();
        RainbowGraphics graphics = rainbowDrawer.getGraphics();
        if (graphics != null) {
            graphics.dispose();
        }
        drawingView = null;
        rainbowDrawer.setGraphics(null);
        rainbowDrawer = null;
        rainbowInputController = null;
        drawingTask = null;
    }

    public void onSketchDestroy() {
        //no-op
    }

    private void shutDownExecutioner() {
        try {
            drawingScheduler.shutdownNow();
            drawingScheduler.awaitTermination(DrawingTask.TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return View where the drawing is performed
     */
    public RainbowTextureView getDrawingView() {
        return drawingView;
    }

    /**
     * @return the View where this rainbow sketch has been injected to
     */
    public ViewParent getParentView() {
        return drawingView.getParent();
    }

    /**
     * Used to retrieve a RainbowDrawer object.
     * <p/>
     * If you need to call this manually, probably you will also need need to call
     * rainbowDrawer.beginDraw() and rainbowDrawer.endDraw() to make your drawing effective.
     * <p/>
     * Also, be aware of drawing offline. Drawing outside of the UI thread is allowed here,
     * and long running drawings will block the UI thread.
     *
     * @return RainbowDrawer, used to draw into the rainbow sketch
     */
    public RainbowDrawer getRainbowDrawer() {
        return rainbowDrawer;
    }

    /**
     * @return RainbowInputController, used to control user interaction with the rainbow sketch
     */
    public RainbowInputController getRainbowInputController() {
        return rainbowInputController;
    }

    public float frameRate() {
        return frameRate;
    }

    public int frameCount() {
        return frameCount;
    }

    public void frameRate(final float newRateTarget) {
        frameRate = newRateTarget;
        if (!isPaused()) {
            pause();
            resume();
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void invalidate() {
        setupDrawingSurface(rainbowDrawer.getGraphics());
    }

    private void setupDrawingSurface(RainbowGraphics graphics) {
        final int newWidth = drawingView.getWidth();
        final int newHeight = drawingView.getHeight();
        if ((newWidth != width) || (newHeight != height)) {
            width = newWidth;
            height = newHeight;
            graphics.setSize(width, height);
        }
        surfaceReady = true;
    }

    class DrawingTask extends TimerTask {
        private static final long TIMEOUT = 10;
        private static final float SECOND = 1000F;

        @Override
        public void run() {
            if (!isPaused() && !drawingScheduler.isShutdown()) {
                handleDraw();
            }
        }

        public long getDelay() {
            return Math.max(1, (long) (SECOND / frameRate));
        }
    }
}
