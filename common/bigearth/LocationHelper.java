package bigearth;

import java.util.regex.*;

public class LocationHelper
{
	private LocationHelper() {} //prevent instantiation of this class

	static Pattern edgePattern = Pattern.compile("E<(\\d+),(\\d+)>");
	static Pattern vertexPattern = Pattern.compile("V<(\\d+),(\\d+),(\\d+)>");

	public static Location parse(String locStr, WorldConfig world)
	{
		Matcher m1 = vertexPattern.matcher(locStr);
		if (m1.matches())
		{
			int r1 = Integer.parseInt(m1.group(1));
			int r2 = Integer.parseInt(m1.group(2));
			int r3 = Integer.parseInt(m1.group(3));
			return world.getGeometry().getVertex(r1,r2,r3);
		}

		Matcher m2 = edgePattern.matcher(locStr);
		if (m2.matches())
		{
			int r1 = Integer.parseInt(m2.group(1));
			int r2 = Integer.parseInt(m2.group(2));
			return world.getGeometry().getEdgeBetween(r1, r2);
		}

		return new SimpleLocation(Integer.parseInt(locStr));
	}
}
