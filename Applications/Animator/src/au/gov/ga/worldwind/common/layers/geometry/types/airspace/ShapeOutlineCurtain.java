package au.gov.ga.worldwind.common.layers.geometry.types.airspace;

import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.airspaces.AirspaceAttributes;
import gov.nasa.worldwind.render.airspaces.Curtain;
import gov.nasa.worldwind.render.airspaces.Geometry;

import javax.media.opengl.GL;

import au.gov.ga.worldwind.animator.layers.immediate.ImmediateMode;

/**
 * An extension of the {@link Curtain} airspace that can render the 
 * generating shape as a line at the upper and lower elevations.
 */
public class ShapeOutlineCurtain extends Curtain implements ShapeOutlineAirspace
{
	private static final int GEOMETRY_TYPE_ELEMENT = 1;
	private static final int GEOMETRY_TYPE_VERTEX = 2;

	boolean drawCurtain = true;
	boolean drawUpperShapeOutline = false;
	boolean drawLowerShapeOutline = false;
	
	public ShapeOutlineCurtain()
	{
		super();
	}

	public ShapeOutlineCurtain(AirspaceAttributes attributes)
	{
		super(attributes);
	}

	public ShapeOutlineCurtain(Iterable<? extends LatLon> locations)
	{
		super(locations);
	}

	@Override
	protected void doRenderGeometry(DrawContext dc, String drawStyle)
	{
		if (ImmediateMode.isImmediate())
		{
			setExpiryTime(1);
		}
		if (drawCurtain)
		{
			super.doRenderGeometry(dc, drawStyle);
		}
		if (drawUpperShapeOutline)
		{
			renderUpperShapeOutline(dc);
		}
		if (drawLowerShapeOutline)
		{
			renderLowerShapeOutline(dc);
		}
	}

	private void renderUpperShapeOutline(DrawContext dc)
	{
		Vec4 refCenter = computeReferenceCenter(dc);
		Geometry vertexGeometry = getCurtainGeometry(dc, refCenter).getVertexGeometry();
		
		int count = vertexGeometry.getCount(GEOMETRY_TYPE_VERTEX) - 2;
		int[] shapeIndices = new int[count];
		int i = 0;
		while (i < count)
		{
			shapeIndices[i] = i+1;
			shapeIndices[i+1] = i+3;
			i+=2;
		}
		
		Geometry shapeOutlineElementGeometry = new Geometry();
		shapeOutlineElementGeometry.setElementData(GL.GL_LINES, count, shapeIndices);
		
		drawShapeOutline(dc, refCenter, vertexGeometry, shapeOutlineElementGeometry);
	}

	private void renderLowerShapeOutline(DrawContext dc)
	{
		Vec4 refCenter = computeReferenceCenter(dc);
		Geometry vertexGeometry = getCurtainGeometry(dc, refCenter).getVertexGeometry();
		
		int count = vertexGeometry.getCount(GEOMETRY_TYPE_VERTEX) - 2;
		int[] shapeIndices = new int[count];
		int i = 0;
		while (i < count)
		{
			shapeIndices[i] = i;
			shapeIndices[i+1] = i+2;
			i+=2;
		}
		
		Geometry shapeOutlineElementGeometry = new Geometry();
		shapeOutlineElementGeometry.setElementData(GL.GL_LINES, count, shapeIndices);
		
		drawShapeOutline(dc, refCenter, vertexGeometry, shapeOutlineElementGeometry);
	}
	
	private void drawShapeOutline(DrawContext dc, Vec4 refCenter, Geometry vertexGeometry,
			Geometry shapeOutlineElementGeometry)
	{
		
		dc.getView().pushReferenceCenter(dc, refCenter);
		GL gl = dc.getGL();
		gl.glPushClientAttrib(GL.GL_CLIENT_VERTEX_ARRAY_BIT);
		gl.glPushAttrib(GL.GL_ALL_ATTRIB_BITS);
		try
		{
			// Points are drawn over the line to prevent gaps forming when 
			// antialiasing and smoothing is applied to the line
			setupDrawParams(dc, gl);
			
			gl.glDepthMask(false);
			drawShapeOutlineAsLines(dc, shapeOutlineElementGeometry, vertexGeometry);
			drawShapeOutlineAsPoints(dc, shapeOutlineElementGeometry, vertexGeometry);
			
			gl.glDepthMask(true);
			drawShapeOutlineAsLines(dc, shapeOutlineElementGeometry, vertexGeometry);			
			
		}
		finally
		{
			dc.getView().popReferenceCenter(dc);
			gl.glPopAttrib();
			gl.glPopClientAttrib();
		}
	}

	private void setupDrawParams(DrawContext dc, GL gl)
	{
		gl.glShadeModel(GL.GL_SMOOTH);
		gl.glEnable(GL.GL_LINE_SMOOTH);
		gl.glEnable(GL.GL_POINT_SMOOTH);
		gl.glEnable(GL.GL_BLEND);
		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
		gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
		gl.glHint(GL.GL_POINT_SMOOTH_HINT, GL.GL_NICEST);
		getAttributes().applyOutline(dc, false);
		gl.glPointSize((float)getAttributes().getOutlineWidth());
	}

	private void drawShapeOutlineAsLines(DrawContext dc, Geometry shapeOutlineElementGeometry, Geometry vertexGeometry)
	{
		getRenderer().drawGeometry(dc, shapeOutlineElementGeometry, vertexGeometry);
	}
	
	private void drawShapeOutlineAsPoints(DrawContext dc, Geometry shapeOutlineElementGeometry, Geometry vertexGeometry)
	{
		getRenderer().drawGeometry(dc, 
								   GL.GL_POINTS, 
								   shapeOutlineElementGeometry.getCount(GEOMETRY_TYPE_ELEMENT), 
								   shapeOutlineElementGeometry.getGLType(GEOMETRY_TYPE_ELEMENT), 
								   shapeOutlineElementGeometry.getBuffer(GEOMETRY_TYPE_ELEMENT), 
								   vertexGeometry);
	}
	
	private CurtainGeometry getCurtainGeometry(DrawContext dc, Vec4 refCenter)
	{
		return getCurtainGeometry(dc, locations.size(), locations.toArray(new LatLon[locations.size()]), pathType,
								  splitThreshold, getAltitudes(), isTerrainConforming(), refCenter);
	}

	public void setDrawCurtain(boolean drawCurtain)
	{
		this.drawCurtain = drawCurtain;
	}

	public void setDrawUpperShapeOutline(boolean drawUpperShapeOutline)
	{
		this.drawUpperShapeOutline = drawUpperShapeOutline;
	}

	public void setDrawLowerShapeOutline(boolean drawLowerShapeOutline)
	{
		this.drawLowerShapeOutline = drawLowerShapeOutline;
	}
}