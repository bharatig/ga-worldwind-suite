package au.gov.ga.worldwind.animator.layers.camerapath;

import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.Renderable;

import java.awt.Color;
import java.nio.DoubleBuffer;

import javax.media.opengl.GL;

import au.gov.ga.worldwind.animator.animation.Animation;
import au.gov.ga.worldwind.animator.animation.AnimationContext;
import au.gov.ga.worldwind.animator.animation.AnimationContextImpl;
import au.gov.ga.worldwind.animator.animation.KeyFrame;
import au.gov.ga.worldwind.common.util.HSLColor;

import com.sun.opengl.util.BufferUtil;

/**
 * Base class for camera position paths
 */
public abstract class AbstractCameraPositionPath implements Renderable
{
	private int frameCount;
	
	// Buffers used to draw the eye path
	private DoubleBuffer pathVertexFrontBuffer;
	private DoubleBuffer pathVertexBackBuffer;
	private DoubleBuffer pathColourFrontBuffer;
	private DoubleBuffer pathColourBackBuffer;
	private Object pathBufferLock = new Object();
	private Vec4 pathReferenceCenter;
	
	/** The animation whose camera path is to be displayed on this layer */
	private Animation animation;
	
	public AbstractCameraPositionPath(Animation animation)
	{
		this.animation = animation;
	}

	public void updateAnimation(Animation animation)
	{
		this.animation = animation;
	}
	
	public void recalulatePath()
	{
		populatePathBuffers();
		swapBuffers();
	}
	
	public void resetPath()
	{
		frameCount = animation.getFrameCount();
		this.pathVertexFrontBuffer = BufferUtil.newDoubleBuffer(frameCount * 3);
		this.pathVertexBackBuffer = BufferUtil.newDoubleBuffer(frameCount * 3);
		this.pathColourFrontBuffer = BufferUtil.newDoubleBuffer(frameCount * 3);
		this.pathColourBackBuffer = BufferUtil.newDoubleBuffer(frameCount * 3);
	}
	
	@Override
	public final void render(DrawContext dc)
	{
		drawPath(dc);
	}

	/**
	 * Draw the camera path
	 */
	private void drawPath(DrawContext dc)
	{
		if (frameCount <= 0 || dc.isPickingMode())
		{
			return;
		}
		
		GL gl = dc.getGL();
		gl.glPushClientAttrib(GL.GL_CLIENT_VERTEX_ARRAY_BIT);
		gl.glPushAttrib(GL.GL_ALL_ATTRIB_BITS);
		
		boolean popRefCenter = false;
		if (pathReferenceCenter != null)
		{
			dc.getView().pushReferenceCenter(dc, pathReferenceCenter);
			popRefCenter = true;
		}
		try
		{
			// Points are drawn over the line to prevent gaps forming when 
			// antialiasing and smoothing is applied to the line
			gl.glEnableClientState(GL.GL_VERTEX_ARRAY);
			gl.glEnableClientState(GL.GL_COLOR_ARRAY);
			gl.glShadeModel(GL.GL_SMOOTH);
			gl.glEnable(GL.GL_LINE_SMOOTH);
			gl.glEnable(GL.GL_POINT_SMOOTH);
			gl.glEnable(GL.GL_BLEND);
			gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
			gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
			gl.glHint(GL.GL_POINT_SMOOTH_HINT, GL.GL_NICEST);
			gl.glLineWidth(2.0f);
			gl.glPointSize(2.0f);
			int numberOfPointsInPath = animation.getFrameOfLastKeyFrame() - animation.getFrameOfFirstKeyFrame();
			synchronized (pathBufferLock)
			{
				gl.glColorPointer(3, GL.GL_DOUBLE, 0, pathColourFrontBuffer);
				gl.glVertexPointer(3, GL.GL_DOUBLE, 0, pathVertexFrontBuffer);

				// Draw a smooth line without depth testing, filling gaps with points
				gl.glDepthMask(false);
				gl.glDrawArrays(GL.GL_LINE_STRIP, 0, numberOfPointsInPath);
				gl.glDrawArrays(GL.GL_POINTS, 0, numberOfPointsInPath);
				gl.glDepthMask(true);
				
				// Now redraw the line with depth testing to ensure line looks correct with markers
				gl.glDrawArrays(GL.GL_LINE_STRIP, 0, numberOfPointsInPath);
			}
		}
		finally
		{
			if (popRefCenter)
			{
				dc.getView().popReferenceCenter(dc);
			}
			gl.glPopAttrib();
			gl.glPopClientAttrib();
		}
	}
	
	/**
	 * Populate the update vertex buffers with the coordinates of the 
	 * eye location at each frame in the animation.
	 * <p/>
	 * Vertex buffer has size <code>3*animation.getlastFrame()</code>, with <code>[x,y,z]</code> stored at 
	 * <code>[3*frame, 3*frame+1, 3*frame+2]</code>
	 * <p/>
	 * Colour buffer has size <code>3*animation.getLastFrame()</code> with colours ramping through the HSV colour space
	 * based on the rate of change of the camera eye position. Colours <code>[r,g,b]</code> are stored at <code>[3*frame, 3*frame+1, 3*frame+2]</code>.
	 */
	private void populatePathBuffers()
	{
		AnimationContext context = new AnimationContextImpl(animation);
		pathReferenceCenter = null;
		
		Position previousPathPosition = null;
		Position currentPathPosition = null;
		
		double[] deltas = new double[animation.getFrameOfLastKeyFrame() - animation.getFrameOfFirstKeyFrame()];
		double minDelta = Double.MAX_VALUE;
		double maxDelta = 0d;
		int i = 0;

		pathVertexBackBuffer.rewind();
		for (int frame = animation.getFrameOfFirstKeyFrame(); frame < animation.getFrameOfLastKeyFrame(); frame ++)
		{
			currentPathPosition = getPathPositionAtFrame(context, frame);
			
			// Populate the vertex buffer
			Vec4 eyeVector = context.getView().getGlobe().computePointFromPosition(currentPathPosition);
			if (pathReferenceCenter == null)
			{
				pathReferenceCenter  = eyeVector; // Choose the first point in the path to be the reference point
			}
			pathVertexBackBuffer.put(eyeVector.x - pathReferenceCenter.x);
			pathVertexBackBuffer.put(eyeVector.y - pathReferenceCenter.y);
			pathVertexBackBuffer.put(eyeVector.z - pathReferenceCenter.z);
			
			// Populate the delta array
			if (previousPathPosition != null)
			{
				double positionDelta = calculateDelta(currentPathPosition, previousPathPosition);
				deltas[i] = positionDelta;
				if (positionDelta > maxDelta)
				{
					maxDelta = positionDelta;
				}
				if (positionDelta < minDelta)
				{
					minDelta = positionDelta;
				}
			}
			previousPathPosition = currentPathPosition;
			i++;
		}
		
		populatePathColourBufferFromDeltas(deltas, minDelta, maxDelta);
		
		pathVertexBackBuffer.rewind();
	}

	/**
	 * @return The position of the camera path at the given frame
	 */
	protected abstract Position getPathPositionAtFrame(AnimationContext context, int frame);

	/**
	 * @return The delta of the current and previous position
	 */
	private double calculateDelta(Position currentPosition, Position previousPosition)
	{
		AnimationContext context = new AnimationContextImpl(animation);
		Vec4 current = context.getView().getGlobe().computePointFromPosition(currentPosition);
		Vec4 previous = context.getView().getGlobe().computePointFromPosition(previousPosition);
		return Math.abs(current.distanceTo3(previous));
	}

	private void populatePathColourBufferFromDeltas(double[] deltas, double minDelta, double maxDelta)
	{
		double deltaWindow = maxDelta - minDelta;
		if (deltaWindow < 1)
		{
			deltaWindow = 1;
		}
		
		// Use the HSL colour ramp to indicate magnitude of deltas
		HSLColor hslColor = new HSLColor(0, 50, 50);
		Color pathColor = null;
		
		pathColourBackBuffer.rewind();
		for (int i = 0; i < deltas.length; i++)
		{
			pathColor = hslColor.adjustHue((float)((deltas[i] - minDelta) / deltaWindow) * 360f);
			pathColourBackBuffer.put((double)pathColor.getRed() / 255d);
			pathColourBackBuffer.put((double)pathColor.getGreen() / 255d);
			pathColourBackBuffer.put((double)pathColor.getBlue() / 255d);
		}
		pathColourBackBuffer.rewind();
	}
	
	/**
	 * @return Whether the provided key frame is one that should be included in this camera path
	 */
	protected abstract boolean isPathFrame(KeyFrame keyFrame);
	
	/**
	 * Swap the front and back buffers, giving access to the newly updated data for drawing
	 */
	private void swapBuffers()
	{
		synchronized (pathBufferLock)
		{
			DoubleBuffer tmp = pathVertexFrontBuffer;
			pathVertexFrontBuffer = pathVertexBackBuffer;
			pathVertexBackBuffer = tmp;
			pathVertexFrontBuffer.rewind();
			
			tmp = pathColourFrontBuffer;
			pathColourFrontBuffer = pathColourBackBuffer;
			pathColourBackBuffer = tmp;
			pathColourFrontBuffer.rewind();
		}
	}
	
	public Animation getAnimation()
	{
		return animation;
	}
}
