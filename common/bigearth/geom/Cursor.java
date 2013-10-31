package bigearth.geom;

/**
 * Refers to a specific face on a polyhedron, and simultaneously
 * one of the sides/corners of that face.
 */
public class Cursor
{
	/** Refers to a "face" of the polyhedron. */
	public int location;

	/** Identifies a side/corner of the selected face.
	 * Ranges from 0 to n-1, where n is the number of sides
	 * (or number of corners) of this face.
	 * Increasing orientation represent moving counter-clockwise
	 * around the face. The corner identified by a given
	 * orientation is always the corner preceding the equivalent
	 * side when moving counter-clockwise.
	 */
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
