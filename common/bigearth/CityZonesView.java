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

	public CityZonesView()
	{
		setPreferredSize(new Dimension(48*GRIDWIDTH,48*GRIDHEIGHT));
		addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent ev) { onMousePressed(ev); }
			public void mouseReleased(MouseEvent ev) { onMouseReleased(ev); }
			});
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

	private void onMousePressed(MouseEvent ev)
	{
		if (zones == null)
			return;

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
	}

	private void drawZone(Graphics2D gr, Rectangle rect, ZoneInfo zone)
	{
		URL zoneIconUrl = zone.type.getIconResource();
		if (zoneIconUrl != null)
		{
			ImageIcon zoneIcon = new ImageIcon(zoneIconUrl);
			gr.drawImage(zoneIcon.getImage(), rect.x, rect.y, null);
		}
	}

	public void update(CityInfo city)
	{
		assert city.hasZones();

		this.zones = city.zones;
		repaint();
	}
}
