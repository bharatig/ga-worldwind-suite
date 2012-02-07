package au.gov.ga.worldwind.viewer.layers.mouse;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.event.PositionEvent;
import gov.nasa.worldwind.event.PositionListener;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.AbstractLayer;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.WWIcon;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import nasa.worldwind.render.offset.IconRenderer;

public class MouseLayer extends AbstractLayer implements PositionListener
{
	private final IconRenderer iconRenderer = new IconRenderer();
	private final Component wwd;
	private final WWIcon icon;
	private final List<WWIcon> icons = new ArrayList<WWIcon>();
	private final Cursor blankCursor;
	private boolean mouseReplaced = false;

	public MouseLayer(WorldWindow wwd, WWIcon icon)
	{
		if (!(wwd instanceof Component))
		{
			throw new IllegalArgumentException("WorldWindow must be a subclass of component");
		}
		wwd.addPositionListener(this);
		this.wwd = (Component) wwd;
		this.iconRenderer.setPedestal(null);
		this.icon = icon;
		icons.add(icon);

		setPickEnabled(false);

		Toolkit tk = Toolkit.getDefaultToolkit();
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		blankCursor = tk.createCustomCursor(image, new Point(0, 0), "BlackCursor");
	}

	@Override
	protected void doRender(DrawContext dc)
	{
		this.iconRenderer.render(dc, icons);
	}

	@Override
	protected void doPick(DrawContext dc, Point pickPoint)
	{
		//don't pick
	}

	@Override
	public void moved(PositionEvent event)
	{
		if (isEnabled())
		{
			Position position = event.getPosition();
			if (position == null)
			{
				icon.setVisible(false);
				restoreMouse();
			}
			else
			{
				icon.setVisible(true);
				icon.setPosition(position);
				replaceMouse();
			}
		}
	}

	private void replaceMouse()
	{
		wwd.setCursor(blankCursor);
		mouseReplaced = true;
	}

	private void restoreMouse()
	{
		wwd.setCursor(null);
		mouseReplaced = false;
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);
		if (!enabled && mouseReplaced)
		{
			restoreMouse();
		}
	}
}