package bigearth;

import java.awt.*;
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

	public CityZonesView()
	{
		setPreferredSize(new Dimension(48*GRIDWIDTH,48*GRIDHEIGHT));
	}

	@Override
	public void paintComponent(Graphics gr1)
	{
		Graphics2D gr = (Graphics2D) gr1;
		Dimension sz = getSize();
		Point origin = new Point(
			sz.width/2 - (GRIDWIDTH*CELLWIDTH)/2,
			sz.height/2 - (GRIDHEIGHT*CELLHEIGHT)/2
			);

		BufferedImage biomeImage = WorldView.biomeTextures.get(BiomeType.PLAINS);
		TexturePaint p = new TexturePaint(biomeImage, new Rectangle(0,0,CELLWIDTH,CELLHEIGHT));
		gr.setPaint(p);
		gr.fillRect(0,0,sz.width,sz.height);

		for (ZoneInfo zone : zones.values())
		{
			Rectangle rect = new Rectangle(
				origin.x+zone.gridx*CELLWIDTH,
				origin.y+zone.gridy*CELLHEIGHT,
				CELLWIDTH,
				CELLHEIGHT
				);
			drawZone(gr, rect, zone);
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
