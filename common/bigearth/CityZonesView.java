package bigearth;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.*;
import javax.swing.*;

public class CityZonesView extends JComponent
{
	Map<String,ZoneInfo> zones;
	static final int GRIDWIDTH = 8;
	static final int GRIDHEIGHT = 8;
	static final int CELLWIDTH = 48;
	static final int CELLHEIGHT = 48;
	Point origin = new Point(0,0);
	String selectedZone;
	ArrayList<Listener> listeners = new ArrayList<>();

	static class NewBuildingCursorConfig
	{
		ZoneType type;

		NewBuildingCursorConfig(ZoneType type)
		{
			this.type = type;
		}
	}
	NewBuildingCursorConfig showNewBuildingCursor = null;

	class NewBuildingCursor
	{
		int gridx;
		int gridy;
		boolean legal;

		Rectangle getBounds()
		{
			return new Rectangle(
				origin.x + CELLWIDTH * gridx,
				origin.y + CELLHEIGHT * gridy,
				CELLWIDTH, CELLHEIGHT);
		}
	}
	NewBuildingCursor newBuildingCursor;

	public CityZonesView()
	{
		setPreferredSize(new Dimension(CELLWIDTH*GRIDWIDTH,CELLHEIGHT*GRIDHEIGHT));

		MouseAdapter mouse = new MouseAdapter() {
			public void mousePressed(MouseEvent ev) { onMousePressed(ev); }
			public void mouseReleased(MouseEvent ev) { onMouseReleased(ev); }
			public void mouseMoved(MouseEvent ev) { onMouseMoved(ev); }
			public void mouseExited(MouseEvent ev) { onMouseExited(ev); }
			};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	public void cancelNewBuildingCursor()
	{
		showNewBuildingCursor = null;
		newBuildingCursor = null;
		repaint();
	}

	public void showNewBuildingCursor(ZoneType type)
	{
		selectedZone = null;
		showNewBuildingCursor = new NewBuildingCursorConfig(type);
		newBuildingCursor = null;
		repaint();
	}

	private Rectangle getZoneRectangle(ZoneInfo zone)
	{
		return new Rectangle(
			origin.x+zone.gridx*CELLWIDTH,
			origin.y+zone.gridy*CELLHEIGHT,
			CELLWIDTH,
			CELLHEIGHT
			);
	}

	private void setNewBuildingCursor(NewBuildingCursor nbc)
	{
		if (this.newBuildingCursor != null)
		{
			repaint(this.newBuildingCursor.getBounds());
		}

		this.newBuildingCursor = nbc;

		if (this.newBuildingCursor != null)
		{
			repaint(this.newBuildingCursor.getBounds());
		}
	}
		
	private void onMouseExited(MouseEvent ev)
	{
		setNewBuildingCursor(null);
	}

	private void onMouseMoved(MouseEvent ev)
	{
		if (showNewBuildingCursor == null)
			return;

		int gridx = (ev.getPoint().x - origin.x) / CELLWIDTH;
		int gridy = (ev.getPoint().y - origin.y) / CELLHEIGHT;
		if (gridx < 0 || gridx >= GRIDWIDTH
			|| gridy < 0 || gridy >= GRIDHEIGHT
			|| ev.getPoint().x < origin.x
			|| ev.getPoint().y < origin.y)
		{
			setNewBuildingCursor(null);
			return;
		}

		NewBuildingCursor nbc = new NewBuildingCursor();
		nbc.gridx = gridx;
		nbc.gridy = gridy;

		// check whether any existing buildings occupy this space
		nbc.legal = true;
		for (ZoneInfo zone : zones.values())
		{
			if (zone.gridx == nbc.gridx && zone.gridy == nbc.gridy)
			{
				nbc.legal = false;
			}
		}

		setNewBuildingCursor(nbc);
	}

	private void onMousePressed(MouseEvent ev)
	{
		if (zones == null)
			return;

		if (showNewBuildingCursor != null)
		{
			if (newBuildingCursor != null && newBuildingCursor.legal)
			{
				fireNewBuildingRequested(
					showNewBuildingCursor.type,
					newBuildingCursor.gridx,
					newBuildingCursor.gridy
					);
				cancelNewBuildingCursor();
				return;
			}
			return;
		}

		for (Map.Entry<String,ZoneInfo> e : zones.entrySet())
		{
			ZoneInfo zone = e.getValue();
			Rectangle r = getZoneRectangle(zone);
			if (r.contains(ev.getPoint()))
			{
				onZonePressed(e.getKey(), zone);
			}
		}
	}

	private void onZonePressed(String zoneName, ZoneInfo zone)
	{
		if (selectedZone != null)
		{
			repaint(getZoneRectangle(zones.get(selectedZone)));
		}

		selectedZone = zoneName;
		repaint(getZoneRectangle(zone));
	}

	private void onMouseReleased(MouseEvent ev)
	{
	}

	@Override
	public void paintComponent(Graphics gr1)
	{
		Graphics2D gr = (Graphics2D) gr1;
		Dimension sz = getSize();
		origin = new Point(
			sz.width/2 - (GRIDWIDTH*CELLWIDTH)/2,
			sz.height/2 - (GRIDHEIGHT*CELLHEIGHT)/2
			);

		BufferedImage biomeImage = WorldView.biomeTextures.get(BiomeType.PLAINS);
		TexturePaint p = new TexturePaint(biomeImage, new Rectangle(0,0,CELLWIDTH,CELLHEIGHT));
		gr.setPaint(p);
		gr.fillRect(0,0,sz.width,sz.height);

		for (ZoneInfo zone : zones.values())
		{
			Rectangle rect = getZoneRectangle(zone);
			drawZone(gr, rect, zone);
		}

		if (selectedZone != null)
		{
			ZoneInfo zone = zones.get(selectedZone);
			if (zone != null)
			{
				Rectangle rect = getZoneRectangle(zone);
				gr.setColor(new Color(255,255,255,128));
				gr.fill(rect);
			}
		}
		if (newBuildingCursor != null)
		{
			Rectangle rect = newBuildingCursor.getBounds();
			gr.setColor(newBuildingCursor.legal ?
				new Color(255, 255, 255, 128) :
				new Color(255, 0, 0, 128)
				);
			gr.fill(rect);
		}
	}

	private void drawZone(Graphics2D gr, Rectangle rect, ZoneInfo zone)
	{
		URL zoneIconUrl = zone.type.getIconResource();
		if (zoneIconUrl != null)
		{
			ImageIcon zoneIcon = new ImageIcon(zoneIconUrl);
			gr.drawImage(zoneIcon.getImage(), rect.x, rect.y, null);
		}

		CommodityType commodity = null;
		if (zone.hasCommodity())
		{
			commodity = zone.commodity;
		}
		else if (zone.hasRecipe())
		{
			commodity = zone.recipe.getOutputCommodity();
		}

		if (commodity != null)
		{
			URL commodityIconUrl = commodity.getIconResource();
			if (commodityIconUrl != null)
			{
				ImageIcon icon = new ImageIcon(commodityIconUrl);
				gr.drawImage(icon.getImage(),
					rect.x + rect.width - icon.getIconWidth(),
					rect.y + rect.height - icon.getIconHeight(),
					null
					);
			}
		}
	}

	public void update(CityInfo city)
	{
		assert city.hasZones();

		this.zones = city.zones;
		repaint();
	}

	public void addListener(Listener l)
	{
		listeners.add(l);
	}

	private void fireNewBuildingRequested(ZoneType type, int gridx, int gridy)
	{
		for (Listener l : listeners)
		{
			l.newBuildingRequested(type, gridx, gridy);
		}
	}

	public interface Listener
	{
		void newBuildingRequested(ZoneType type, int gridx, int gridy);
	}
}
