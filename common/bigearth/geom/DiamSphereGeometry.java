package bigearth.geom;

/**
 * Subdivides each face of a decahedron into a grid of smaller faces.
 */
public class DiamSphereGeometry extends GridifyGeometry
{
	public DiamSphereGeometry(int size)
	{
		super(new Decahedron(), size);
	}

	@Override
	public String toString()
	{
		return "diamsphere:"+size;
	}
}
