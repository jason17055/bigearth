package bigearth;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.vecmath.*;
import javax.imageio.stream.*;

public class WorldView extends JPanel
	implements AncestorListener, MouseListener, MouseMotionListener, MouseWheelListener,
		MapModel.Listener, MobListModel.Listener
{
	MapModel map;
	MobListModel mobs;

	int [] colors; //overlay colors
	BufferedImage image; //terrain image
	boolean terrainDirty;

	AbstractProjection mapProj;
	MapProjection curProjection;

	ArrayList<Listener> listeners;
	boolean showRivers;
	boolean allowVertexSelection;
	boolean allowCitySelection = true;
	boolean allowMobSelection = true;
	boolean showCoordinateLines = false;
	boolean showMobImages = true;
	boolean showCities = true;

	Selection selection = new Selection();

	class Selection
	{
		int selectedRegion;
		Geometry.VertexId selectedVertex;
		String selectedMob;
		Location selectedCity;

		public Location getLocation()
		{
			if (selectedVertex != null)
			{
				return selectedVertex;
			}
			else if (selectedRegion != 0)
			{
				return new SimpleLocation(selectedRegion);
			}
			else
			{
				assert false;
				return null;
			}
		}

		public void clear()
		{
			this.selectedRegion = 0;
			this.selectedVertex = null;
			this.selectedMob = null;
			this.selectedCity = null;

			onSelectionChanged();
			repaint();
		}

		public Location getCity()
		{
			assert isCity();
			return selectedCity;
		}

		public String getMob()
		{
			assert isMob();
			return selectedMob;
		}

		public int getRegion()
		{
			assert isRegion();
			return selectedRegion;
		}

		public boolean isCity()
		{
			return selectedCity != null;
		}

		public boolean isLocation()
		{
			return selectedRegion != 0 || selectedVertex != null;
		}

		public boolean isMob()
		{
			return selectedMob != null;
		}

		public boolean isRegion()
		{
			return selectedRegion != 0;
		}

		public boolean isSelectedMob(String mobName)
		{
			return selectedMob != null && selectedMob.equals(mobName);
		}

		public void selectCity(Location cityLocation)
		{
			assert cityLocation != null;

			this.selectedRegion = 0;
			this.selectedVertex = null;
			this.selectedMob = null;
			this.selectedCity = cityLocation;

			onSelectionChanged();
			fireCitySelected(this.selectedCity);
			repaint();
		}

		public void selectMob(String mobName)
		{
			assert mobName != null;

			this.selectedRegion = 0;
			this.selectedVertex = null;
			this.selectedMob = mobName;
			this.selectedCity = null;

			onSelectionChanged();
			onMobSelected(mobName);
			fireMobSelected(this.selectedMob);
			repaint();
		}

		public void selectRegion(int regionId)
		{
			assert regionId != 0;

			this.selectedRegion = regionId;
			this.selectedVertex = null;
			this.selectedMob = null;
			this.selectedCity = null;

			onSelectionChanged();
			onRegionSelected(this.selectedRegion);
			fireRegionSelected(this.selectedRegion);
			repaint();
		}

		public void selectVertex(Geometry.VertexId vtx)
		{
			assert vtx != null;

			this.selectedRegion = 0;
			this.selectedVertex = vtx;
			this.selectedMob = null;
			this.selectedCity = null;

			onSelectionChanged();
			fireVertexSelected(this.selectedVertex);
			repaint();
		}
	}

	public void ancestorAdded(AncestorEvent evt)
	{
	}

	public void ancestorMoved(AncestorEvent evt)
	{
	}

	public void ancestorRemoved(AncestorEvent evt)
	{
		stopAnimating();
	}

	void onSelectionChanged()
	{
		if (mobFocusAnimationTimer != null)
		{
			mobFocusAnimationTimer.stop();
			mobFocusAnimationTimer = null;
		}
	}

	void stopAnimating()
	{
		if (mobFocusAnimationTimer != null)
		{
			mobFocusAnimationTimer.stop();
			mobFocusAnimationTimer = null;
		}
	}

	javax.swing.Timer mobFocusAnimationTimer;
	void onMobSelected(String mobName)
	{
		if (mobFocusAnimationTimer != null)
		{
			mobFocusAnimationTimer.stop();
			mobFocusAnimationTimer = null;
		}

		animationIndex = 0;
		ActionListener taskPerformer = new ActionListener() {
		public void actionPerformed(ActionEvent ev)
		{
			animationIndex++;
			repaintSelectedMob();
		}
		};

		// start animation
		mobFocusAnimationTimer = new javax.swing.Timer(140, taskPerformer);
		mobFocusAnimationTimer.start();
	}

	static final int UNKNOWN_BIOME_COLOR = 0x888888;
	static final int DEFAULT_WIDTH = 720;
	static final int DEFAULT_HEIGHT = 360;

	public WorldView()
	{
		listeners = new ArrayList<Listener>();

		setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
		setProjection(MapProjection.SIMPLE);
		addAncestorListener(this);
		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
		showRivers = true;
	}

	public void setMap(MapModel map)
	{
		if (this.map != null)
			this.map.removeListener(this);

		this.map = map;

		if (this.map != null)
		{
			this.colors = new int[map.getGeometry().getCellCount()];
			this.map.addListener(this);
		}
		else
		{
			this.colors = null;
		}
	}

	public void setMobs(MobListModel mobs)
	{
		if (this.mobs != null)
			this.mobs.removeListener(this);

		this.mobs = mobs;

		if (this.mobs != null)
		{
			this.mobs.addListener(this);
		}
	}

	public interface Listener
	{
		void onCitySelected(Location cityLocation);
		void onMobSelected(String mobName);
		void onRegionSelected(int regionId);
		void onVertexSelected(Geometry.VertexId vertex);
	}

	public void addListener(Listener l)
	{
		listeners.add(l);
	}

	public void removeListener(Listener l)
	{
		listeners.remove(l);
	}

	public Location getSelectedLocation()
	{
		if (selection.isLocation())
		{
			return selection.getLocation();
		}
		else
		{
			return null;
		}
	}

	class MapPainter
	{
		BufferedImage image;
		int width;
		int height;

		MapPainter(BufferedImage image)
		{
			this.image = image;
			this.width = image.getWidth();
			this.height = image.getHeight();
		}

		boolean tryPixel(int x, int y, int c)
		{
			if (x >= 0 && x < width && y >= 0 && y < height)
			{
				if ((image.getRGB(x,y) & 0xffffff) == 0)
				{
					image.setRGB(x,y,c);
					return true;
				}
			}
			return false;
		}
	}

	void generateImage(SphereGeometry g, int [] colors)
	{
		this.colors = colors;
		terrainDirty = true;
		repaint();
	}

	void drawMap(BufferedImage image, Point [] pts)
	{
		assert image != null;
		assert pts != null;
		assert pts.length == colors.length;

		Geometry g = map.getGeometry();
		int numRegions = g.getCellCount();
		assert numRegions == pts.length;

		int [] todo = new int[numRegions];
		int curCount = 0;
		for (int i = 0; i < numRegions; i++)
		{
			Point3d regionPt = g.getCenterPoint(i+1);
			if (mapProj.isVisible(regionPt)) {
				todo[curCount++] = i;
			}
		}

		MapPainter mp = new MapPainter(image);

		int radius = 0;
		while (todo.length != 0 && radius<100)
		{
			int [] next = new int[curCount];
			int nextCount = 0;
			for (int ii = 0; ii < curCount; ii++)
			{
				int i = todo[ii];
				boolean flag = false;
				int x = pts[i].x;
				int y = pts[i].y;
				int col = colors[i];
				if (col == 0)
				{
					RegionProfile r = map.getRegion(i+1);
					if (r != null)
					{
					BiomeType biome = map.getRegion(i+1).getBiome();
					if (biomeColors.containsKey(biome))
						col = biomeColors.get(biome);
					else
						col = UNKNOWN_BIOME_COLOR;
					}
					else
						col = UNKNOWN_BIOME_COLOR;
				}

				if (radius == 0)
				{
					if (mp.tryPixel(x, y, col))
						flag = true;
				}
				else
				for (int j = 0; j < radius; j++)
				{
					if (mp.tryPixel(x-radius+j, y-j, col))
						flag = true;
					if (mp.tryPixel(x+j, y-radius+j, col))
						flag = true;
					if (mp.tryPixel(x+radius-j, y+j, col))
						flag = true;
					if (mp.tryPixel(x-j, y+radius-j, col))
						flag = true;
				}
					
				if (flag)
					next[nextCount++] = i;
			}
			todo = next;
			curCount = nextCount;
			radius++;
		}
	}

	Rectangle [] regionBounds;
	void updateRegionBounds(Point [] pts)
	{
		assert map != null;

		Geometry g = map.getGeometry();
		int numRegions = g.getCellCount();

		assert pts.length == numRegions;

		regionBounds = new Rectangle[numRegions];
		for (int i = 0; i < numRegions; i++)
		{
			int min_x = pts[i].x;
			int min_y = pts[i].y;
			int max_x = pts[i].x;
			int max_y = pts[i].y;

			for (int n : g.getNeighbors(i+1))
			{
				Point p = pts[n-1];
				if (p.x > -getWidth() && p.x < min_x) min_x = p.x;
				if (p.y > -getHeight() && p.y < min_y) min_y = p.y;
				if (p.x < 2*getWidth() && p.x > max_x) max_x = p.x;
				if (p.y < 2*getHeight() && p.y > max_y) max_y = p.y;
			}
			regionBounds[i] = new Rectangle(
				min_x,
				min_y,
				max_x-min_x+1,
				max_y-min_y+1);
		}
	}

	void regenerateTerrainImage()
	{
		if (map == null)
			return;

		mapProj.setOffset(getWidth()/2, getHeight()/2);

		Geometry g = map.getGeometry();

		int numRegions = colors.length;
		assert numRegions == g.getCellCount();

		Point [] pts = new Point[numRegions];
		for (int i = 0; i < pts.length; i++)
		{
			pts[i] = mapProj.toScreen(g.getCenterPoint(i+1));
		}

		updateRegionBounds(pts);

		Rectangle screen = new Rectangle(0,0,getWidth(),getHeight());

		this.image = new BufferedImage(getWidth(),getHeight(),
				BufferedImage.TYPE_INT_RGB);
		Graphics2D gr = image.createGraphics();

		if (mapProj.zoomFactor < 8)
		{
			drawMap(image, pts);
		}
		else
		{
			//
			// fill region areas
			//
			for (int i = 0; i < regionBounds.length; i++)
			{
				if (!screen.intersects(regionBounds[i]))
					continue;

				Point3d [] bb = g.getCellBoundary(i+1);
				int [] x_coords = new int[bb.length];
				int [] y_coords = new int[bb.length];
				toScreen_a(bb, x_coords, y_coords);

				if (colors[i] != 0)
				{
					gr.setColor(new Color(colors[i]));
					gr.fillPolygon(x_coords, y_coords, bb.length);
				}
				else
				{
					RegionProfile r = map.getRegion(i+1);
					drawRegionArea(gr, i+1, r, x_coords, y_coords);
				}
			}

		if (showRivers)
		{
			//
			// draw region sides
			//
			for (int i = 0; i < regionBounds.length; i++)
			{
				if (!screen.intersects(regionBounds[i]))
					continue;

				Point3d [] bb = g.getCellBoundary(i+1);
				int [] x_coords = new int[bb.length];
				int [] y_coords = new int[bb.length];
				toScreen_a(bb, x_coords, y_coords);

				RegionProfile r = map.getRegion(i+1);
				drawRegionBorder(gr, i+1, r, x_coords, y_coords);
			}

			//
			// draw region corners
			//
			for (int i = 0; i < regionBounds.length; i++)
			{
				if (!screen.intersects(regionBounds[i]))
					continue;

				Point3d [] bb = g.getCellBoundary(i+1);
				int [] x_coords = new int[bb.length];
				int [] y_coords = new int[bb.length];
				toScreen_a(bb, x_coords, y_coords);

				RegionProfile r = map.getRegion(i+1);
				drawRegionCorners(gr, i+1, r, x_coords, y_coords);
			}
		}

			//
			// draw region emblems
			//
			for (int i = 0; i < regionBounds.length; i++)
			{
				if (!screen.intersects(regionBounds[i]))
					continue;

				Point3d pt = g.getCenterPoint(i+1);
				Point p = mapProj.toScreen(pt);
				RegionProfile r = map.getRegion(i+1);
				drawRegionEmblem(gr, i+1, r, p);
			}

		} //end if zoom factor >= 8

		terrainDirty = false;
	}

	// implements MapModel.Listener
	public void regionUpdated(Location loc)
	{
		SwingUtilities.invokeLater(new Runnable() {
		public void run()
		{
			terrainDirty = true;
			repaint();
		}});
	}

	// implements MobListModel.Listener
	public void mobRemoved(String mobName, MobInfo.RemovalDisposition disposition)
	{
		if (selection.isMob() && selection.getMob().equals(mobName))
		{
			selection.clear();
		}
		repaint();
	}

	// implements MobListModel.Listener
	public void mobUpdated(String mobName)
	{
		repaint();
	}

	static final Color MOB_TACK_FILL_COLOR = new Color(0xff8778);
	static final Color MOB_TACK_STROKE_COLOR = new Color(0xa9463f);
	static final int MOB_TACK_SM_DIAMETER = 8;
	static final int MOB_TACK_LG_DIAMETER = 20;
	static final int MOB_TACK_LG_HEIGHT = 32;

	void drawMobDot(Graphics gr, Point p, int regionId)
	{
		final int d = MOB_TACK_SM_DIAMETER;

		gr.setColor(MOB_TACK_FILL_COLOR);
		gr.fillOval(p.x-d/2,p.y-d/2,d, d);
		gr.setColor(MOB_TACK_STROKE_COLOR);
		gr.drawOval(p.x-d/2,p.y-d/2,d, d);
	}

	void drawMobPin(Graphics gr, Point p, int regionId)
	{
		Graphics2D gr2 = (Graphics2D) gr;

		int z = (int) Math.floor(MOB_TACK_LG_DIAMETER/2.0 / Math.sqrt(2));

		int x0 = p.x;
		int x1 = x0 - MOB_TACK_LG_DIAMETER / 2;
		int x2 = x1 + MOB_TACK_LG_DIAMETER;
		int x3 = p.x - z;
		int x4 = p.x + z;

		int y0 = p.y - MOB_TACK_LG_HEIGHT;
		int y1 = y0 + MOB_TACK_LG_DIAMETER / 2 - z;
		int y2 = y0 + MOB_TACK_LG_DIAMETER / 2;
		int y3 = y2 + z;
		int y4 = p.y;

		int [] x_coords = new int[]
		{ x0, x4, x2, x4, x0, x3, x1, x3 };
		int [] y_coords = new int[]
		{ y4, y3, y2, y1, y0, y1, y2, y3 };

		Shape s = new Polygon(x_coords, y_coords, x_coords.length);

		drawMobDot(gr, p, regionId);

		gr2.setColor(MOB_TACK_FILL_COLOR);
		gr2.fill(s);
		gr2.setColor(MOB_TACK_STROKE_COLOR);
		gr2.draw(s);
	}

	void drawGreatCircle(Graphics g, Point3d fromPt, Point3d toPt)
	{
		Point p1 = mapProj.toScreen(fromPt);
		Point p2 = mapProj.toScreen(toPt);

		if (Math.abs(p1.x - p2.x) + Math.abs(p1.y - p2.y) > 10)
		{
			Vector3d v = new Vector3d();
			v.sub(fromPt, toPt);
			if (v.length() < 0.0001)
				return;

			v.add(fromPt, toPt);
			v.normalize();
			Point3d centerPt = new Point3d(v);

			drawGreatCircle(g, fromPt, centerPt);
			drawGreatCircle(g, centerPt, toPt);
		}
		else if (Math.abs(p1.x - p2.x) + Math.abs(p1.y - p2.y) < 32)
		{
			g.drawLine(p1.x, p1.y, p2.x, p2.y);
		}
	}

	void drawCoordinateLines(Graphics g)
	{
		g.setColor(new Color(0x888888));
		for (int i = -2; i <= 2; i++)
		{
			double lat = i * Math.PI/6;
			for (int j = -6; j < 6; j++)
			{
				double lgt_1 = j * Math.PI/6;
				double lgt_2 = (j+1) * Math.PI/6;
				drawGreatCircle(g,
					SphereGeometry.fromPolar(lgt_1, lat),
					SphereGeometry.fromPolar(lgt_2, lat)
					);
			}
		}

		for (int i = -5; i <= 6; i++)
		{
			double lgt = i * Math.PI/6;
			for (int j = -3; j < 3; j++)
			{
				double lat_1 = j * Math.PI/6;
				double lat_2 = (j+1) * Math.PI/6;
				drawGreatCircle(g,
					SphereGeometry.fromPolar(lgt, lat_1),
					SphereGeometry.fromPolar(lgt, lat_2)
					);
			}
		}
	}

	private static void drawRiver(Graphics gr, int x0, int y0, int x1, int y1)
	{
		// draw shaft of arrow
		gr.drawLine(x0, y0, x1, y1);
	}

	private static void drawArrow(Graphics gr, int x0, int y0, int x1, int y1)
	{
		// draw shaft of arrow
		gr.drawLine(x0, y0, x1, y1);

		// draw head
		int dx = (x1 - x0) / 5;
		int dy = (y1 - y0) / 5;

		gr.drawLine(x1, y1, x1-dx-dy, y1-dy+dx);
		gr.drawLine(x1, y1, x1-dx+dy, y1-dy-dx);
	}

	static Map<BiomeType, BufferedImage> biomeTextures;
	static Map<BiomeType, Rectangle> biomeMappingRect;
	static Map<BiomeType, Integer> biomeColors;
	static
	{
		biomeTextures = new EnumMap<BiomeType, BufferedImage>(BiomeType.class);
		loadTexture(BiomeType.OCEAN, "ocean");
		loadTexture(BiomeType.DESERT, "desert");
		loadTexture(BiomeType.FOREST, "forest");
		loadTexture(BiomeType.GLACIER, "glacier");
		loadTexture(BiomeType.GRASSLAND, "grassland");
		loadTexture(BiomeType.HILLS, "hills");
		loadTexture(BiomeType.JUNGLE, "jungle");
		loadTexture(BiomeType.MOUNTAIN, "mountains");
		loadTexture(BiomeType.PLAINS, "plains");
		loadTexture(BiomeType.SWAMP, "swamp");
		loadTexture(BiomeType.TUNDRA, "tundra");
		loadTexture(BiomeType.LAKE, "ocean");

		biomeMappingRect = new EnumMap<BiomeType, Rectangle>(BiomeType.class);
		for (BiomeType b : biomeTextures.keySet())
		{
			BufferedImage img = biomeTextures.get(b);
			Rectangle r = new Rectangle(0, 0,
				img.getWidth(),
				img.getHeight()
				);
			biomeMappingRect.put(b, r);
		}

		biomeColors = new EnumMap<BiomeType, Integer>(BiomeType.class);
		biomeColors.put(BiomeType.OCEAN, 0x1e41a4);
		biomeColors.put(BiomeType.LAKE, 0x1e41a4);
		biomeColors.put(BiomeType.DESERT, 0xddbb70);
		biomeColors.put(BiomeType.FOREST, 0x337118);
		biomeColors.put(BiomeType.GLACIER, 0xf6f6f6);
		biomeColors.put(BiomeType.GRASSLAND, 0x0a8403);
		biomeColors.put(BiomeType.HILLS, 0x3f8020);
		biomeColors.put(BiomeType.JUNGLE, 0x367f26);
		biomeColors.put(BiomeType.MOUNTAIN, 0x858767);
		biomeColors.put(BiomeType.PLAINS, 0x7e9e32);
		biomeColors.put(BiomeType.SWAMP, 0x365436);
		biomeColors.put(BiomeType.TUNDRA, 778362);

		try
		{
		loadMobSelectionAnimation();
		}
		catch (IOException e)
		{
			e.printStackTrace(System.err);
		}
	}

	static Map<MobType, BufferedImage> mobImages = new HashMap<MobType, BufferedImage>();
	static BufferedImage loadMobImage(MobType mobType)
	{
		if (!mobImages.containsKey(mobType))
		{
			URL avatarUrl = mobType.getAvatarIconResource();
			if (avatarUrl == null)
				return null;

			try
			{
				BufferedImage img = ImageIO.read(avatarUrl);
				mobImages.put(mobType, img);
			}
			catch (IOException e)
			{
				System.err.println(e);
				return null;
			}
		}
		return mobImages.get(mobType);
	}

	static BufferedImage loadCityImage()
	{
		URL cityUrl = WorldView.class.getResource("/city_images/city1.png");
		try
		{
			BufferedImage img = ImageIO.read(cityUrl);
			return img;
		}
		catch (IOException e)
		{
			System.err.println(e);
			return null;
		}
	}

	static void loadTexture(BiomeType biome, String textureName)
	{
		try
		{
			String resourceName = "/terrain_textures/"+textureName+".png";
			URL textureUrl = WorldView.class.getResource(resourceName);
			if (textureUrl == null)
				throw new IOException("texture resource not found: "+resourceName);

			BufferedImage img = ImageIO.read(textureUrl);
			biomeTextures.put(biome, img);
		}
		catch (IOException e)
		{
			//FIXME- do something?
System.err.println("Warning: could not load "+textureName+" texture");
System.err.println(e);
		}
	}

	void drawRegionEmblem(Graphics2D gr, int regionId, RegionProfile r, Point p)
	{
		if (r.hasEmblem(RegionEmblem.GOLD))
		{
			drawRegionEmblemReal(gr, p, RegionEmblem.GOLD);
			return;
		}
		else if (r.hasEmblem(RegionEmblem.STOCKPILE))
		{
			drawRegionEmblemReal(gr, p, RegionEmblem.STOCKPILE);
			return;
		}

		// auto-generate a stockpile emblem in some cases

		if (!r.hasStock())
			return;

		if (r.hasCitySize() && r.citySize != 0)
			return;

		if (r.stock.isEmpty())
			return;

		drawRegionEmblemReal(gr, p, RegionEmblem.STOCKPILE);
	}

	HashMap<RegionEmblem, ImageIcon> emblemImages = new HashMap<>();
	private void drawRegionEmblemReal(Graphics2D gr, Point p, RegionEmblem emblem)
	{
		URL iconUrl = emblem.getIconResource();
		if (iconUrl != null)
		{
			ImageIcon icon = new ImageIcon(iconUrl);
			gr.drawImage(icon.getImage(), p.x - icon.getIconWidth()/2, p.y - icon.getIconHeight()/2, null);
		}
		else
		{
			int [] x_coords = new int[] { p.x - 8, p.x, p.x + 8 };
			int [] y_coords = new int[] { p.y + 4, p.y - 10, p.y + 4 };

			gr.setColor(Color.MAGENTA);
			gr.fillPolygon(x_coords, y_coords, x_coords.length);
		}
	}

	void drawRegionArea(Graphics gr, int regionId, RegionProfile r, int [] x_coords, int [] y_coords)
	{
		assert r != null;

		Graphics2D gr2 = (Graphics2D) gr;
		Paint oldPaint = gr2.getPaint();

		BiomeType biome = r.getBiome();
		if (biomeTextures.containsKey(biome))
		{
			Rectangle rect = biomeMappingRect.get(biome);
			TexturePaint p = new TexturePaint(biomeTextures.get(biome), rect);
			gr2.setPaint(p);
		}
		else
		{
			gr2.setColor(new Color(UNKNOWN_BIOME_COLOR));
		}

		gr2.fillPolygon(x_coords, y_coords, x_coords.length);
		gr2.setPaint(oldPaint);
	}

	void drawCities(Graphics2D gr)
	{
		Geometry g = map.getGeometry();
		for (int i = 0; i < regionBounds.length; i++)
		{
			Rectangle rb = regionBounds[i];
			if (!gr.hitClip(rb.x, rb.y, rb.width, rb.height))
				continue;

			RegionProfile r = map.getRegion(i+1);
			if (!r.hasCitySize())
				continue;

			Point p = mapProj.toScreen(g.getCenterPoint(i+1));
			CityInfo city = new CityInfo();
			city.displayName = r.cityName;
			drawCity(gr, p, city);
		}
	}

	void drawRegionBorder(Graphics gr, int regionId, RegionProfile r, int [] x_coords, int [] y_coords)
	{
		int n = x_coords.length;

		if (r == null)
			return;

		gr.setColor(Color.BLACK);
		for (int i = 0; i < n; i++)
		{
			RegionSideDetail.SideFeature sf = r.getSideFeature(i);
			if (sf.isRiver())
			{
				Graphics2D gr2 = (Graphics2D) gr;

				Stroke oldStroke = gr2.getStroke();
				if (sf != RegionSideDetail.SideFeature.BROOK)
				{

				gr2.setStroke(new BasicStroke(
					sf == RegionSideDetail.SideFeature.RIVER ? 8.0f : 5.0f
					));
				gr2.setColor(new Color(170,149,53));

				drawRiver(gr,
					x_coords[(i+n-1)%n],
					y_coords[(i+n-1)%n],
					x_coords[i],
					y_coords[i]
					);
				}

				if (sf != RegionSideDetail.SideFeature.BROOK
					|| mapProj.zoomFactor > 16)
				{

				gr2.setStroke(new BasicStroke(
					sf == RegionSideDetail.SideFeature.RIVER ? 3.0f :
					sf == RegionSideDetail.SideFeature.CREEK ? 2.0f : 1.0f));
				gr2.setColor(Color.BLUE);

				drawRiver(gr,
					x_coords[(i+n-1)%n],
					y_coords[(i+n-1)%n],
					x_coords[i],
					y_coords[i]
					);

				}

				gr2.setStroke(oldStroke);
			}
		}
	}

	void drawRegionCorners(Graphics gr, int regionId, RegionProfile r, int [] x_coords, int [] y_coords)
	{
		if (r == null)
			return;

		int n = x_coords.length;
		for (int i = 0; i < n; i++)
		{
			if (r.corners[i] != null)
			{
				drawRegionCorner(gr, regionId, r, i, x_coords[i], y_coords[i]);
			}
		}
	}

	void drawRegionCorner(Graphics gr, int regionId, RegionProfile r, int cornerIdx, int x, int y)
	{
		Graphics2D gr2 = (Graphics2D) gr;

		RegionCornerDetail.PointFeature type = r.getCornerFeature(cornerIdx);
		int radius = type == RegionCornerDetail.PointFeature.LAKE ? 10 : 5;

		Paint oldPaint = gr2.getPaint();
		Stroke oldStroke = gr2.getStroke();

		gr2.setStroke(new BasicStroke(5.0f));
		final Color RIVER_BANK_COLOR = new Color(170,149,53);
		gr2.setColor(RIVER_BANK_COLOR);
		gr.drawOval(x-radius,y-radius,2*radius, 2*radius);
		gr2.setStroke(oldStroke);

		if (biomeTextures.containsKey(BiomeType.LAKE))
		{
			gr2.setPaint(new TexturePaint(
				biomeTextures.get(BiomeType.LAKE),
				biomeMappingRect.get(BiomeType.LAKE)
				));
		}
		else
		{
			gr.setColor(new Color(biomeColors.get(BiomeType.LAKE)));
		}

		gr.fillOval(x-radius,y-radius,2*radius,2*radius);

		gr2.setPaint(oldPaint);
	}

	public void paintComponent(Graphics gr_x)
	{
		Graphics2D gr = (Graphics2D) gr_x;

		if (terrainDirty || image == null
			|| image.getWidth() != getWidth()
			|| image.getHeight() != getHeight())
		{
			regenerateTerrainImage();
		}

		if (image == null || regionBounds == null)
			return;

		// draw the terrain
		gr.drawImage(image, 0, 0, Color.WHITE, null);

		// latitude/longitude lines
		if (showCoordinateLines)
			drawCoordinateLines(gr);

		if (showCities)
		{
			drawCities(gr);
		}

		if (showMobImages && mobs != null)
		{
			drawMobs(gr);
		}

		// draw selection rectangle
		if (selection.isRegion())
		{
			drawSelectionRect(gr);
		}
		else if (selection.isMob())
		{
			MobInfo mob = mobs.mobs.get(selection.getMob());
			assert mob != null;
			assert mob.location != null;

			Point p = mapProj.toScreen(map.getGeometry().getPoint(mob.location));
			drawFleetSelectionBack(gr, p);
			drawMob(gr, p, mob);
			drawFleetSelectionCircle(gr, p);
		}
	}

	int animationIndex = 0;
	void repaintSelectedMob()
	{
		MobInfo mob = mobs.mobs.get(selection.getMob());
		if (mob == null)
			return;

		Location loc = mob.location;
		Point p = mapProj.toScreen(map.getGeometry().getPoint(loc));

		Graphics2D gr = (Graphics2D) getGraphics();
		drawFleetSelectionBack(gr, p);
		drawMob(gr, p, mob);
		drawFleetSelectionCircle(gr, p);
	}

	static final int ACTIVITY_IND_SIZE = 16;
	static final Dimension FLAG_IND_SIZE = new Dimension(16, 10);

	void drawCity(Graphics2D gr, Point p, CityInfo city)
	{
		final int CAPTION_Y_OFFSET = 16;
		final int CAPTION_PADDING = 4;
		final int CAPTION_FONT_SIZE = 18;

		BufferedImage img = loadCityImage();
		if (img != null)
		{
			int width = img.getWidth(null);
			int height = img.getHeight(null);
			gr.drawImage(img, p.x - width/2, p.y - height/2, null);
		}

		if (city.hasDisplayName())
		{
			Font oldFont = gr.getFont();

			gr.setFont(new Font("Verdana", Font.BOLD, CAPTION_FONT_SIZE));
			FontMetrics metrics = gr.getFontMetrics();
			int hgt = metrics.getHeight();
			int adv = metrics.stringWidth(city.displayName);
			Dimension sz = new Dimension(adv, hgt);

			gr.setColor(new Color(0,0,0,128));
			gr.fillRect(p.x-(sz.width+CAPTION_PADDING*2)/2,
				p.y+CAPTION_Y_OFFSET,
				sz.width+CAPTION_PADDING*2,
				sz.height+CAPTION_PADDING*2);

			gr.setColor(Color.WHITE);
			gr.drawString(city.displayName,
				p.x-sz.width/2,
				p.y+CAPTION_Y_OFFSET+CAPTION_PADDING+sz.height-metrics.getDescent());

			gr.setFont(oldFont);
		}
	}

	void drawFlag(Graphics2D gr, Rectangle rect, Flag flag)
	{
		gr.setColor(flag == Flag.RED ? Color.RED :
			flag == Flag.BLUE ? Color.BLUE : Color.WHITE);
		gr.fill(rect);
		gr.setColor(Color.BLACK);
		gr.draw(rect);
	}

	void drawMob(Graphics2D gr, Point p, MobInfo mob)
	{
		assert gr != null;
		assert p != null;
		assert mob != null;

		// TODO- skip if this point is clearly off the screen

		//	if (zoomFactor <= 2)
		//		drawMobDot(gr, p, regionId);
		//	else
		//		drawMobPin(gr, p, regionId);

		assert mob.hasMobType();
		BufferedImage img = loadMobImage(mob.mobType);
		assert img != null;

			int width = img.getWidth(null);
			int height = img.getHeight(null);
			gr.drawImage(img, p.x - width/2, p.y - height/2, null);

		Rectangle flagRect = new Rectangle(
			p.x - width/2,
			p.y - height/2,
			FLAG_IND_SIZE.width,
			FLAG_IND_SIZE.height);
		if (mob.hasFlag() && mob.flag != Flag.NONE)
		{
			drawFlag(gr, flagRect, mob.flag);
		}

		Rectangle indRect = new Rectangle(
			p.x + width/2 - ACTIVITY_IND_SIZE,
			p.y - height/2,
			ACTIVITY_IND_SIZE,
			ACTIVITY_IND_SIZE);

		if (mob.activity != null &&
			!mob.activity.equals(""))
		{
			gr.setColor(Color.WHITE);
			gr.fill(indRect);
			gr.setColor(Color.BLACK);
			gr.draw(indRect);

			String sym = getActivitySymbol(mob.activity);
			FontMetrics metrics = gr.getFontMetrics();
			int hgt = metrics.getAscent();
			int adv = metrics.stringWidth(sym);
			Dimension sz = new Dimension(adv, hgt);
			gr.drawString(sym,
				indRect.x+indRect.width/2-sz.width/2,
				indRect.y+indRect.height/2+sz.height/2);
		}
	}

	static String getActivitySymbol(String activityName)
	{
		assert activityName != null;
		assert !activityName.equals("");

		if (activityName.equals("gather-wood") ||
			activityName.equals("gather-minerals") ||
			activityName.equals("hunt"))
		{
			return "M"; // M == Mine
		}
		else if (activityName.equals("build-city"))
		{
			return "B"; // B == Build
		}
		else if (activityName.equals("move"))
		{
			return "G"; // G == Go To
		}
		else
		{
			return "A"; // all other activities
		}
	}

	static BufferedImage [] mobSelectionFrontImages;
	static BufferedImage [] mobSelectionBackImages;

	static void loadMobSelectionAnimation()
		throws IOException
	{
		Iterator readers = ImageIO.getImageReadersByFormatName("gif");
		ImageReader reader = (ImageReader) readers.next();

		InputStream f1 = WorldView.class.getResourceAsStream("/ui_images/fleet_selection_circle_front.gif");
		ImageInputStream iis = ImageIO.createImageInputStream(f1);

		reader.setInput(iis, true);

		ArrayList<BufferedImage> imageArray = new ArrayList<BufferedImage>();
		int count = 0;
		while (true)
		{
			try
			{
				BufferedImage img = reader.read(count);
				imageArray.add(img);
				count++;
			}
			catch (IndexOutOfBoundsException e)
			{
				break;
			}
		}

		mobSelectionFrontImages = imageArray.toArray(new BufferedImage[0]);

		InputStream f2 = WorldView.class.getResourceAsStream("/ui_images/fleet_selection_circle_back.gif");
		iis = ImageIO.createImageInputStream(f2);
		reader.setInput(iis, true);

		imageArray.clear();
		count = 0;
		while (true)
		{
			try
			{
				BufferedImage img = reader.read(count);
				imageArray.add(img);
				count++;
			}
			catch (IndexOutOfBoundsException e)
			{
				break;
			}
		}
		mobSelectionBackImages = imageArray.toArray(new BufferedImage[0]);
	}

	void drawFleetSelectionCircle(Graphics gr, Point p)
	{
		int count = mobSelectionFrontImages.length;
		BufferedImage img = mobSelectionFrontImages[animationIndex % count];
		int width = img.getWidth(null);
		int height = img.getHeight(null);
		gr.drawImage(img, p.x - width/2, p.y - height/2, null);
	}

	void drawFleetSelectionBack(Graphics gr, Point p)
	{
		int count = mobSelectionBackImages.length;
		BufferedImage img = mobSelectionBackImages[animationIndex % count];
		int width = img.getWidth(null);
		int height = img.getHeight(null);
		gr.drawImage(img, p.x - width/2, p.y - height/2, null);
	}

	void drawSelectionRect(Graphics gr)
	{
		Geometry g = map.getGeometry();
		Point3d [] pp = g.getCellBoundary(selection.getRegion());

		int [] x_coords = new int[pp.length];
		int [] y_coords = new int[pp.length];
		toScreen_a(pp, x_coords, y_coords);

		gr.setColor(Color.YELLOW);
		gr.drawPolygon(x_coords, y_coords, pp.length);
	}

	void drawMobs(Graphics2D gr)
	{
		assert map != null;
		assert regionBounds != null;
		assert mobs != null;

		Geometry g = map.getGeometry();
		Rectangle screen = gr.getClipBounds();

		// draw mobs
		for (String mobName : mobs.mobs.keySet())
		{
			if (selection.isSelectedMob(mobName))
				continue;

			MobInfo mob = mobs.mobs.get(mobName);
			Location loc = mob.location;

			Point p = mapProj.toScreen(g.getPoint(loc));
			drawMob(gr, p, mob);
		}

	}

	// implements MouseWheelListener
	public void mouseWheelMoved(MouseWheelEvent ev)
	{
		if (ev.getWheelRotation() > 0)
			zoomOut();
		else
			zoomIn();
	}

	// implements MouseListener
	public void mouseClicked(MouseEvent ev) { }

	// implements MouseListener
	public void mouseEntered(MouseEvent ev) { }

	// implements MouseListener
	public void mouseExited(MouseEvent ev) { }

	// implements MouseListener
	public void mousePressed(MouseEvent ev)
	{
		if (map == null)
			return;

		if (ev.getButton() == MouseEvent.BUTTON1)
		{
			dragStart = ev.getPoint();
			dragStartPt = mapProj.fromScreen(dragStart);

			if (dragStartPt != null) {
				selectNearestTo(dragStartPt);
			}
		}
		else if (ev.getButton() == MouseEvent.BUTTON3)
		{
			Point3d pt = mapProj.fromScreen(ev.getPoint());
			if (pt != null) {
				int regionId = map.getGeometry().findCell(pt);
				onRightMouseClick(regionId);
			}
		}
	}

	protected void onRightMouseClick(int regionId)
	{
	}

	private void selectNearestTo(Point3d pt)
	{
		Geometry g = map.getGeometry();
		int regionId = g.findCell(pt);
		if (allowMobSelection && mobs != null)
		{
			// check whether any mobs are at this location
			SimpleLocation loc = new SimpleLocation(regionId);
			for (Map.Entry<String, MobInfo> x : mobs.mobs.entrySet())
			{
				String mobName = x.getKey();
				MobInfo mob = x.getValue();
				if (mob.location.equals(loc))
				{
					selection.selectMob(mobName);
					return;
				}
			}
		}

		if (allowCitySelection && map != null)
		{
			// check for a city at this location
			RegionProfile r = map.getRegion(regionId);
			if (r.hasCitySize())
			{
				Location cityLocation = new SimpleLocation(regionId);
				selection.selectCity(cityLocation);
				return;
			}
		}

		if (!allowVertexSelection)
		{
			selection.selectRegion(regionId);
			return;
		}

		Point3d regionCenterPoint = g.getCenterPoint(regionId);
		Vector3d v = new Vector3d();
		v.sub(pt, regionCenterPoint);
		double r_dist = v.length();

		Point3d [] borderPoints = g.getCellBoundary(regionId);
		int best = -1;
		for (int i = 0; i < borderPoints.length; i++)
		{
			v.sub(pt, borderPoints[i]);
			double n_dist = v.length();
			if (n_dist < r_dist)
			{
				best = i;
				r_dist = n_dist;
			}
		}

		if (best == -1)
		{
			selection.selectRegion(regionId);
			return;
		}

		Geometry.VertexId [] vtxs = g.getSurroundingVertices(regionId);
		selection.selectVertex(vtxs[best]);
	}

	protected void onRegionSelected(int regionId)
	{
	}

	private void fireCitySelected(Location cityLocation)
	{
		for (Listener l : listeners)
			l.onCitySelected(cityLocation);
	}

	private void fireMobSelected(String mobName)
	{
		for (Listener l : listeners)
			l.onMobSelected(mobName);
	}

	private void fireRegionSelected(int regionId)
	{
		for (Listener l : listeners)
			l.onRegionSelected(regionId);
	}

	private void fireVertexSelected(Geometry.VertexId vertex)
	{
		for (Listener l : listeners)
			l.onVertexSelected(vertex);
	}

	public void zoomIn()
	{
		mapProj.scale(2.0);
		terrainDirty = true;
		repaint();
	}

	public void zoomOut()
	{
		mapProj.scale(0.5);
		terrainDirty = true;
		repaint();
	}

	// implements MouseListener
	public void mouseReleased(MouseEvent ev)
	{
		if (ev.getButton() == MouseEvent.BUTTON1 && dragStartPt != null)
		{
			int d = Math.abs(ev.getX()-dragStart.x)
				+ Math.abs(ev.getY()-dragStart.y);
			if (d>5)
			{
				onDragEnd(ev.getPoint());
			}
			dragStartPt = null;
		}
		else if (ev.getButton() == MouseEvent.BUTTON3)
		{ //right-click
		}
	}

	// implements MouseMotionListener
	public void mouseDragged(MouseEvent ev)
	{
		if (dragStartPt != null)
		{
			onDragged(ev.getPoint());
		}
	}

	// implements MouseMotionListener
	public void mouseMoved(MouseEvent ev) {}

	private Point dragStart;
	private Point3d dragStartPt;
	private void onDragEnd(Point endPoint)
	{
		mapProj.panTo(dragStartPt, endPoint);
		dragStartPt = null;

		terrainDirty = true;
		repaint();
	}

	private void onDragged(Point curPoint)
	{
		//System.out.println(curPoint.x - dragStart.x);
	}

	public void panTo(Location loc)
	{
		Point3d pt = map.getGeometry().getPoint(loc);
		panTo(pt);
	}

	public void panTo(Point3d pt)
	{
		mapProj.panTo(pt, getScreenCenter());

		terrainDirty = true;
		repaint();
	}

	void toScreen_a(Point3d [] pp, int [] x_coords, int [] y_coords)
	{
		for (int i = 0; i < pp.length; i++) {
			Point p = mapProj.toScreen(pp[i]);
			x_coords[i] = p.x;
			y_coords[i] = p.y;
		}
	}

	private Point getScreenCenter()
	{
		return new Point(
			getWidth() / 2,
			getHeight() / 2
			);
	}

	public void setProjection(MapProjection newProjection)
	{
		AbstractProjection newProj;
		switch (newProjection) {
		case ORTHOGRAPHIC:
			newProj = new OrthographicProjection();
			break;
		default:
			newProj = new SimpleProjection();
			break;
		}

		this.curProjection = newProjection;
		if (this.mapProj != null) {
			newProj.copySettingsFrom(mapProj);
		}
		this.mapProj = newProj;
		terrainDirty = true;
		repaint();
	}
}
