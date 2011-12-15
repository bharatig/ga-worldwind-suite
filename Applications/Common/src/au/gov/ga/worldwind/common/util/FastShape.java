package au.gov.ga.worldwind.common.util;

import gov.nasa.worldwind.cache.Cacheable;
import gov.nasa.worldwind.geom.Extent;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sphere;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.Renderable;
import gov.nasa.worldwind.util.BufferWrapper;
import gov.nasa.worldwind.util.OGLStackHandler;

import java.awt.Color;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.media.opengl.GL;

import com.sun.opengl.util.BufferUtil;

/**
 * The FastShape class is a representation of a piece of geometry. It is useful
 * for meshes or points or lines with a large number of vertices, as the vertex
 * positions aren't updated every frame (instead they are updated in a vertex
 * updater thread).
 * 
 * @author Michael de Hoog
 */
public class FastShape implements Renderable, Cacheable
{
	//TODO this class can probably be generalised to support other geometry types, and add VBO support

	protected List<Position> positions;
	protected final Object positionLock = new Object();

	protected FloatBuffer colorBuffer;
	protected int colorBufferElementSize = 3;
	protected IntBuffer[] indices;
	protected int mode;

	protected DoubleBuffer vertexBuffer;
	protected DoubleBuffer modVertexBuffer;
	protected DoubleBuffer normalBuffer;
	protected DoubleBuffer modNormalBuffer;
	protected Sphere boundingSphere;
	protected Sphere modBoundingSphere;
	protected final Object vertexLock = new Object();

	protected Color color = Color.white;
	protected double opacity = 1;
	protected boolean followTerrain = false;

	protected double lastVerticalExaggeration = -1;
	protected Globe lastGlobe = null;
	protected boolean verticesDirty = true;

	protected double elevation = 0d;
	protected boolean calculateNormals = false;
	protected boolean fogEnabled = false;

	public FastShape(List<Position> positions, int mode)
	{
		this(positions, null, mode);
	}

	public FastShape(List<Position> positions, IntBuffer[] indices, int mode)
	{
		setPositions(positions);
		setIndices(indices);
		setMode(mode);
	}

	@Override
	public void render(DrawContext dc)
	{
		recalculateIfRequired(dc);

		synchronized (vertexLock)
		{
			if (vertexBuffer == null || vertexBuffer.limit() <= 0)
				return;

			if (boundingSphere == null || !dc.getView().getFrustumInModelCoordinates().intersects(boundingSphere))
				return;

			GL gl = dc.getGL();
			OGLStackHandler stack = new OGLStackHandler();

			try
			{
				stack.pushAttrib(gl, GL.GL_CURRENT_BIT | GL.GL_FOG_BIT);
				stack.pushClientAttrib(gl, GL.GL_CLIENT_VERTEX_ARRAY_BIT);
				dc.getView().pushReferenceCenter(dc, boundingSphere.getCenter());

				if (!fogEnabled)
				{
					gl.glDisable(GL.GL_FOG);
				}

				if (colorBuffer != null)
				{
					gl.glEnableClientState(GL.GL_COLOR_ARRAY);
					gl.glColorPointer(colorBufferElementSize, GL.GL_FLOAT, 0, colorBuffer.rewind());
				}

				double alpha = getOpacity();
				if (dc.getCurrentLayer() != null)
				{
					alpha *= dc.getCurrentLayer().getOpacity();
				}
				gl.glColor4d(color.getRed() / 255d, color.getGreen() / 255d, color.getBlue() / 255d, alpha);
				if (alpha < 1.0)
				{
					gl.glEnable(GL.GL_BLEND);
					gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
				}

				gl.glEnableClientState(GL.GL_VERTEX_ARRAY);
				gl.glVertexPointer(3, GL.GL_DOUBLE, 0, vertexBuffer.rewind());

				if (isCalculateNormals() && getMode() == GL.GL_TRIANGLES)
				{
					gl.glEnableClientState(GL.GL_NORMAL_ARRAY);
					gl.glNormalPointer(GL.GL_DOUBLE, 0, normalBuffer.rewind());
				}

				if (indices == null)
				{
					gl.glDrawArrays(mode, 0, vertexBuffer.limit() / 3);
				}
				else
				{
					for (IntBuffer ind : indices)
					{
						gl.glDrawElements(mode, ind.limit(), GL.GL_UNSIGNED_INT, ind.rewind());
					}
				}
			}
			finally
			{
				stack.pop(gl);
				dc.getView().popReferenceCenter(dc);
			}
		}
	}

	protected void recalculateIfRequired(DrawContext dc)
	{
		boolean recalculate = followTerrain || lastVerticalExaggeration != dc.getVerticalExaggeration();
		boolean recalculateNow = verticesDirty || lastGlobe != dc.getGlobe();
		if (recalculate || recalculateNow)
		{
			lastVerticalExaggeration = dc.getVerticalExaggeration();
			lastGlobe = dc.getGlobe();
			recalculateVertices(dc, recalculateNow);
			verticesDirty = false;
		}
	}

	protected synchronized void recalculateVertices(final DrawContext dc, boolean runNow)
	{
		Runnable runnable = new Runnable()
		{
			@Override
			public void run()
			{
				ensureBuffersExist();

				calculateVertices(dc);
				calculateNormals();

				synchronized (vertexLock)
				{
					DoubleBuffer temp = vertexBuffer;
					vertexBuffer = modVertexBuffer;
					modVertexBuffer = temp;
					Sphere temps = boundingSphere;
					boundingSphere = modBoundingSphere;
					modBoundingSphere = temps;
					if (isCalculateNormals())
					{
						temp = normalBuffer;
						normalBuffer = modNormalBuffer;
						modNormalBuffer = temp;
					}
				}
			}
		};

		if (runNow)
		{
			runnable.run();
		}
		else
		{
			VertexUpdater.run(this, runnable);
		}
	}

	protected void calculateVertices(DrawContext dc)
	{
		synchronized (positionLock)
		{
			modVertexBuffer.rewind();
			Globe globe = dc.getGlobe();
			for (LatLon position : positions)
			{
				double elevation = this.elevation;
				if (followTerrain)
				{
					elevation += globe.getElevation(position.getLatitude(), position.getLongitude());
				}
				if (position instanceof Position)
				{
					elevation += ((Position) position).getElevation();
				}
				elevation *= dc.getVerticalExaggeration();
				elevation = Math.max(elevation, -dc.getGlobe().getMaximumRadius());
				Vec4 v =
						dc.getGlobe().computePointFromPosition(position.getLatitude(), position.getLongitude(),
								elevation);
				modVertexBuffer.put(v.x).put(v.y).put(v.z);
			}

			modVertexBuffer.rewind();
			BufferWrapper wrapper = new BufferWrapper.DoubleBufferWrapper(modVertexBuffer);
			modBoundingSphere = Sphere.createBoundingSphere(wrapper);

			modVertexBuffer.rewind();
			for (int i = 0; modVertexBuffer.remaining() >= 3; i += 3)
			{
				modVertexBuffer.put(i + 0, modVertexBuffer.get() - modBoundingSphere.getCenter().x);
				modVertexBuffer.put(i + 1, modVertexBuffer.get() - modBoundingSphere.getCenter().y);
				modVertexBuffer.put(i + 2, modVertexBuffer.get() - modBoundingSphere.getCenter().z);
			}
		}
	}

	protected void calculateNormals()
	{
		if (isCalculateNormals() && getMode() == GL.GL_TRIANGLES)
		{
			int size = modNormalBuffer.limit() / 3;
			int[] count = new int[size];

			if (indices == null)
			{
				//TODO
			}
			else
			{
				Vec4[] vertices = new Vec4[size];
				Vec4[] normals = new Vec4[size];

				int j = 0;
				modVertexBuffer.rewind();
				while (modVertexBuffer.hasRemaining())
				{
					vertices[j] = new Vec4(modVertexBuffer.get(), modVertexBuffer.get(), modVertexBuffer.get());
					normals[j] = new Vec4(0);
					j++;
				}

				for (IntBuffer ib : indices)
				{
					//don't touch ib's position/mark, because it may currently be in use by OpenGL thread
					for (int i = 0; i < ib.limit(); i += 3)
					{
						int index0 = ib.get(i);
						int index1 = ib.get(i + 1);
						int index2 = ib.get(i + 2);
						Vec4 v0 = vertices[index0];
						Vec4 v1 = vertices[index1];
						Vec4 v2 = vertices[index2];

						Vec4 e1 = v1.subtract3(v0);
						Vec4 e2 = v2.subtract3(v0);
						Vec4 N = e1.cross3(e2).normalize3(); // if N is 0, the triangle is degenerate

						if (N.getLength3() > 0)
						{
							normals[index0] = normals[index0].add3(N);
							normals[index1] = normals[index1].add3(N);
							normals[index2] = normals[index2].add3(N);

							count[index0]++;
							count[index1]++;
							count[index2]++;
						}
					}
				}

				j = 0;
				modNormalBuffer.rewind();
				while (modNormalBuffer.hasRemaining())
				{
					int c = count[j] > 0 ? count[j] : 1; //prevent divide by zero
					modNormalBuffer.put(normals[j].x / c);
					modNormalBuffer.put(normals[j].y / c);
					modNormalBuffer.put(normals[j].z / c);
					j++;
				}
			}
		}
	}

	protected void ensureBuffersExist()
	{
		synchronized (vertexLock)
		{
			int size = positions.size() * 3;
			if (modVertexBuffer == null || modVertexBuffer.limit() != size)
			{
				//vertexBuffer = BufferUtil.newDoubleBuffer(size);
				modVertexBuffer = BufferUtil.newDoubleBuffer(size);
			}
			if (isCalculateNormals() && (modNormalBuffer == null || modNormalBuffer.limit() != size))
			{
				//normalBuffer = BufferUtil.newDoubleBuffer(size);
				modNormalBuffer = BufferUtil.newDoubleBuffer(size);
			}
		}
	}

	public Color getColor()
	{
		return color;
	}

	public void setColor(Color color)
	{
		this.color = color;
	}

	public FloatBuffer getColorBuffer()
	{
		return colorBuffer;
	}

	public void setColorBuffer(FloatBuffer colorBuffer)
	{
		this.colorBuffer = colorBuffer;
	}

	public int getColorBufferElementSize()
	{
		return colorBufferElementSize;
	}

	public void setColorBufferElementSize(int colorBufferElementSize)
	{
		this.colorBufferElementSize = colorBufferElementSize;
	}

	public double getOpacity()
	{
		return opacity;
	}

	public void setOpacity(double opacity)
	{
		this.opacity = opacity;
	}

	public List<? extends LatLon> getPositions()
	{
		return positions;
	}

	public void setPositions(List<Position> positions)
	{
		synchronized (positionLock)
		{
			this.positions = positions;
			verticesDirty = true;
		}
	}

	public IntBuffer[] getIndices()
	{
		return indices;
	}

	public void setIndices(IntBuffer[] indices)
	{
		this.indices = indices;
	}

	public boolean isFollowTerrain()
	{
		return followTerrain;
	}

	public void setFollowTerrain(boolean followTerrain)
	{
		synchronized (positionLock)
		{
			this.followTerrain = followTerrain;
		}
	}

	public int getMode()
	{
		return mode;
	}

	public void setMode(int mode)
	{
		this.mode = mode;
	}

	public double getElevation()
	{
		return elevation;
	}

	public void setElevation(double elevation)
	{
		this.elevation = elevation;
	}

	public boolean isCalculateNormals()
	{
		return calculateNormals;
	}

	public void setCalculateNormals(boolean calculateNormals)
	{
		this.calculateNormals = calculateNormals;
	}

	public boolean isFogEnabled()
	{
		return fogEnabled;
	}

	public void setFogEnabled(boolean fogEnabled)
	{
		this.fogEnabled = fogEnabled;
	}

	@Override
	public long getSizeInBytes()
	{
		//very approximate, measured by checking JVM memory usage over many object creations
		return 500 + 80 * getPositions().size();
	}

	/**
	 * @return The extent of this shape. This is calculated by
	 *         {@link FastShape#render(DrawContext)}, so don't use this for
	 *         frustum culling.
	 */
	public Extent getExtent()
	{
		synchronized (vertexLock)
		{
			return boundingSphere;
		}
	}

	public static FloatBuffer color4ToFloatBuffer(List<Color> colors)
	{
		FloatBuffer buffer = BufferUtil.newFloatBuffer(colors.size() * 4);
		return color4ToFloatBuffer(colors, buffer);
	}

	public static FloatBuffer color4ToFloatBuffer(List<Color> colors, FloatBuffer buffer)
	{
		for (Color color : colors)
		{
			buffer.put(color.getRed() / 255f).put(color.getGreen() / 255f).put(color.getBlue() / 255f)
					.put(color.getAlpha() / 255f);
		}
		buffer.rewind();
		return buffer;
	}

	public static FloatBuffer color3ToFloatBuffer(List<Color> colors)
	{
		FloatBuffer buffer = BufferUtil.newFloatBuffer(colors.size() * 3);
		return color3ToFloatBuffer(colors, buffer);
	}

	public static FloatBuffer color3ToFloatBuffer(List<Color> colors, FloatBuffer buffer)
	{
		for (Color color : colors)
		{
			buffer.put(color.getRed() / 255f).put(color.getGreen() / 255f).put(color.getBlue() / 255f);
		}
		buffer.rewind();
		return buffer;
	}

	private static class VertexUpdater
	{
		private static BlockingQueue<OwnerRunnable> queue = new LinkedBlockingQueue<OwnerRunnable>();
		private static Set<OwnerRunnable> set = Collections.synchronizedSet(new HashSet<OwnerRunnable>());
		private static final int THREAD_COUNT = 1;

		static
		{
			for (int i = 0; i < THREAD_COUNT; i++)
			{
				Thread thread = new Thread(new Runnable()
				{
					@Override
					public void run()
					{
						while (true)
						{
							try
							{
								OwnerRunnable or = queue.take();
								or.runnable.run();
								set.remove(or);
							}
							catch (Throwable t)
							{
								t.printStackTrace();
							}
						}
					}
				});
				thread.setName(VertexUpdater.class.getName());
				thread.setDaemon(true);
				thread.start();
			}
		}

		public synchronized static void run(Object owner, Runnable runnable)
		{
			OwnerRunnable or = new OwnerRunnable(owner, runnable);
			if (!set.contains(or))
			{
				set.add(or);
				queue.add(or);
			}
		}

		private static class OwnerRunnable
		{
			public final Object owner;
			public final Runnable runnable;

			public OwnerRunnable(Object owner, Runnable runnable)
			{
				this.owner = owner;
				this.runnable = runnable;
			}

			@Override
			public int hashCode()
			{
				return owner.hashCode();
			}

			@Override
			public boolean equals(Object obj)
			{
				if (obj == null)
					return false;
				if (obj.equals(owner))
					return true;
				return super.equals(obj);
			}
		}
	}
}
