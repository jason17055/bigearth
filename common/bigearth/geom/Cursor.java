package bigearth.geom;

/**
 * Refers to a specific face on a polyhedron, and simultaneously
 * one of the sides/corners of that face.
 */
public class Cursor
{
	public int location;
	public int orientation;

	public Cursor()
	{
	}

	public Cursor(int location, int orientation)
	{
		this.location = location;
		this.orientation = orientation;
	}

	@Override
	public String toString()
	{
		return location+":"+orientation;
	}
}
