package bigearth;

public class GeometryFactory
{
	public static Geometry getInstance(String geometryName)
	{
		String [] parts = geometryName.split(":");
		if (parts[0].equals("sphere"))
		{
			return new SphereGeometry(
				Integer.parseInt(parts[1])
				);
		}
		throw new UnsupportedGeometryException(geometryName);
	}
}

class UnsupportedGeometryException extends RuntimeException
{
	public UnsupportedGeometryException(String geometryName)
	{
		super("Unsupported Geometry: "+geometryName);
	}
}
