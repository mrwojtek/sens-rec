# Sensors Record Graphics

To use utilities in this directory you need Python 3 with svgwrite module
installed: `pip install svgwrite`.

The `sensrec.py` module generates all the icons used for the Sensors Record
application.  
To do that it uses a set of utilities from `svgpaths.py` module which generate
a quadratic or linear Bézier curves from a given function and its
derivative.
