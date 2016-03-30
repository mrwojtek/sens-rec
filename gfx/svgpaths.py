# -*- coding: utf-8 -*-
#
#  (C) Copyright 2015, 2016 Wojciech Mruczkiewicz
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import math


def __transform_points(points, top_left, bottom_right, xrange, yrange):

    sx = (bottom_right[0] - top_left[0]) / (xrange[1] - xrange[0])
    ox = top_left[0] - xrange[0]*sx
    
    sy = (bottom_right[1] - top_left[1]) / (yrange[1] - yrange[0])
    oy = top_left[1] - yrange[0]*sy
    
    for i in range(len(points)):
        points[i] = (sx*points[i][0] + ox, sy*points[i][1] + oy)
    return points


def __resolve_yrange_linear(yrange, x0, y0, x1, y1):
    ymin, ymax = yrange
    if ymin > y0:
        ymin = y0
    if ymin > y1:
        ymin = y1
    if ymax < y0:
        ymax = y0
    if ymax < y1:
        ymax = y1
    return ymin, ymax


def __resolve_yrange_quadratic(yrange, x0, y0, x1, y1, x2, y2):
        ymin, ymax = __resolve_yrange_linear(yrange, x0, y0, x2, y2)
        dt = y0 - 2.0*y1 + y2
        if math.fabs(dt) > 1e-6:
            t = (y0 - y1) / dt
            if 0.0 <= t <= 1.0:
                y = (1 - t)**2*y0 + 2.0*(1 - t)*t*y1 + t**2*y2
                if ymin > y:
                    ymin = y
                if ymax < y:
                    ymax = y
        return ymin, ymax


def __points_to_path_linear(points, top_left, bottom_right, xrange, yrange):
    points = __transform_points(points, top_left, bottom_right,
                                xrange, yrange)
    d = [('M', points[0][0], points[0][1])]
    for i in range(1, len(points)):
        d.append(('L', points[i][0], points[i][1]))
    return d


def __points_to_path_quadratic(points, top_left, bottom_right, xrange, yrange):
    points = __transform_points(points, top_left, bottom_right,
                                xrange, yrange)
    d = [('M', points[0][0], points[0][1])]
    for i in range(len(points) // 2):
        d.append(('Q', points[2*i+1][0], points[2*i+1][1],
                       points[2*i+2][0], points[2*i+2][1]))
    return d


def __find_control_point(x0, y0, dy0, x2, y2, dy2):
    # Equilinear points
    dy = (y2 - y0) / (x2 - x0)
    if math.fabs(dy - dy0) < 1e-5 and math.fabs(dy - dy2) < 1e-5:
        return (x0 + x2) / 2.0, (y0 + y2) / 2.0
            
    dyd = dy0 - dy2
    
    # Nonequlinear but derivatives equal, need to subdivide
    if math.fabs(dyd) < 1e-6:
        raise ValueError('Nonlinear points with equal derivatives')
        
    x1 = (dy0*x0 - dy2*x2 + y2 - y0) / dyd

    # Outside the x-interval, need to subdivide
    if x1 < x0 or x2 < x1:
        raise ValueError('Control point outside function interval')
    
    y1 = (dy0*(dy2*(x0 - x2) + y2) - dy2*y0) / dyd
        
    return x1, y1


def linear_array(X, Y, top_left, bottom_right):    
    assert len(X) == len(Y), 'X and Y lengths are not the same'
    
    if len(X) > 0:
        xrange = (X[0], X[-1])
        yrange = (math.inf, -math.inf)
        x0, y0 = X[0], Y[0]
        points = [(x0, y0)]
        for i in range(1, len(X)):
            x1, y1 = X[i], Y[i]
            points.append((x1, y1))
            yrange = __resolve_yrange_linear(yrange, x0, y0, x1, y1)
        return __points_to_path_linear(points, top_left, bottom_right,
                                       xrange, yrange)


def quadratic_array(X, Y, DY, top_left, bottom_right):
    assert len(X) == len(Y) and len(X) == len(DY), \
        'X, Y and DY lengths are not the same'
    
    if len(X) > 0:           
        xrange = (X[0], X[-1])
        yrange = (math.inf, -math.inf)
        x0, y0, dy0 = X[0], Y[0], DY[0]
        points = [(x0, y0)]
        for i in range(1, len(X)):
            x2, y2, dy2 = X[i], Y[i], DY[i]
            x1, y1 = __find_control_point(x0, y0, dy0, x2, y2, dy2)
            yrange = __resolve_yrange_quadratic(yrange, x0, y0, x1, y1, x2, y2)
            points.append((x1, y1))
            points.append((x2, y2))
            x0, y0, dy0 = x2, y2, dy2
        
        return __points_to_path_quadratic(points, top_left, bottom_right,
                                          xrange, yrange)


def quadratic_fun(f, df, x0, x1, n, top_left, bottom_right, debug=False):
    if n > 0:                    
        def append_interval(points, yrange, x0, y0, dy0, x2, y2, dy2):
            try:
                x1, y1 = __find_control_point(x0, y0, df(x0), x2, y2, df(x2))
                points.append((x1, y1))
                points.append((x2, y2))
                yrange = __resolve_yrange_quadratic(yrange,
                                                    x0, y0, x1, y1, x2, y2)
            except ValueError:
                if debug:
                    print('Dividing between %f and %f' % (x0, x2))
                xm = (x0 + x2) / 2.0
                ym, dym = f(xm), df(xm)
                yrange = append_interval(points, yrange,
                                         x0, y0, dy0, xm, ym, dym)
                yrange = append_interval(points, yrange,
                                         xm, ym, dym, x2, y2, dy2)      
            return yrange
                
        xrange = (x0, x1)
        yrange = (math.inf, -math.inf)
        dx = (x1 - x0) / (n - 1)
        y0, dy0 = f(x0), df(x0)
        points = [(x0, y0)]
        for i in range(1, n):
            x2 = x0 + dx
            y2, dy2 = f(x2), df(x2)
            yrange = append_interval(points, yrange, x0, y0, dy0, x2, y2, dy2)
            x0, y0, dy0 = x2, y2, dy2
            
        return __points_to_path_quadratic(points, top_left, bottom_right,
                                          xrange, yrange)
