# -*- coding: utf-8 -*-
"""
   (C) Copyright 2015 Wojciech Mruczkiewicz
   
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
   
   Contributors:
       Wojciech Mruczkiewicz
       
"""

import math
import matplotlib.pylab as pl
import numpy as np
import svgwrite
import svgpaths
import os
import re
import subprocess
    
# Circular envelope
#Y = np.sin(4*X*np.pi)*np.sqrt(1 - X**2)
#pl.plot(X, Y, label='circ')

# Gaussian envelope
#A=3*np.pi
#B=2
#def fun(x):
#    return math.sin(A*x)*math.exp(-B*x**2)
#def dfun(x):
#    return (A*math.cos(A*x) - 2.0*B*x*math.sin(A*x))*math.exp(-B*x**2)    
#X = np.linspace(-1, 1, 99)    
#Y = np.sin(A*X)*np.exp(-B*X**2)
#DY = (A*np.cos(A*X) - 2.0*B*X*np.sin(A*X))*np.exp(-B*X**2)
#pl.plot(X, Y, label='gauss')
#pl.plot(X, DY, label='gauss\'')
#pl.legend()

material_grey_50 = svgwrite.rgb(250, 250, 250, 'RGB')
material_grey_50_tint = svgwrite.rgb(252, 252, 252, 'RGB')
material_grey_50_shade = svgwrite.rgb(228, 228, 228, 'RGB')
material_grey_100 = svgwrite.rgb(245, 245, 245, 'RGB')
material_grey_100_tint = svgwrite.rgb(249, 249, 249, 'RGB')
material_grey_100_shade = svgwrite.rgb(224, 224, 224, 'RGB')
material_grey_200 = svgwrite.rgb(238, 238, 238, 'RGB')
material_red_300 = svgwrite.rgb(229, 115, 115, 'RGB')
material_red_400 = svgwrite.rgb(239, 83, 80, 'RGB')
material_red_500 = svgwrite.rgb(244, 67, 54, 'RGB')
material_red_600 = svgwrite.rgb(229, 57, 57, 'RGB')
material_red_700 = svgwrite.rgb(211, 47, 47, 'RGB')
material_red_700_tint = svgwrite.rgb(220, 89, 89, 'RGB')
material_red_700_shade = svgwrite.rgb(181, 45, 45, 'RGB')
material_red_800 = svgwrite.rgb(198, 40, 40, 'RGB')
material_red_900 = svgwrite.rgb(183, 28, 28, 'RGB')

class SensRecFun:
    
    def __init__(self, A, C, D):
        self.A = A
        self.C = C
        self.D = D
        
    def fun(self, x):
        return math.sin(self.C*x) * (1.0 - self.A*x**2) * \
               math.exp(-self.D*x**2)
               
    def dfun(self, x):
        return math.exp(-self.D*x**2) * (self.C*(1.0 - self.A*x**2) * \
               math.cos(self.C*x) +
               2.0*x*(-self.D + self.A*(-1.0 + self.D*x**2)) * \
               math.sin(self.C*x))

def export_icon(prefix, path, width, height, with_xxxhdpi=False):
    m = re.match('(.*)\\.svg', path)
    if m is not None:
        scales = [ ('mdpi', 1, 1), \
                   ('hdpi', 3, 2), \
                   ('xhdpi', 2, 1), \
                   ('xxhdpi', 3, 1) ]
        if with_xxxhdpi:
            scales.append(('xxxhdpi', 4, 1))
        for suffix, p, q in scales:
            dir = '%s-%s' % (prefix, suffix)
            cmd = 'inkscape -z -e %s%s%s.png -w %d -h %d %s' % \
                (dir, os.path.sep, m.group(1) , width*p/q, height*p/q, path)
            print(cmd)
            os.makedirs(dir, exist_ok=True)
            subprocess.call(cmd)
    

def make_launcher_icon(path):
    width = 192
    height = 192
    #padding = 16.0
    padding = 18.0
    stroke = 10.0
    #radius = 16.0
    radius = 20.0
    outer_radius = width/2.0 - padding/2.0
    top_left = (padding + stroke/2.0, padding + stroke/2.0)
    bottom_right = (width - radius - padding, height - padding - stroke/2.0)

    f = SensRecFun(0.5, 3*np.pi, 0.28)
    p = svgpaths.quadratic_fun(f.fun, f.dfun, -1.0, 1.0, 22, \
                                top_left, bottom_right)
                    
    dwg = svgwrite.Drawing(path, size=(width, height), profile='tiny')
    
    # Background
    dwg.add(dwg.ellipse((width/2.0, height/2.0 - 1), \
                        (outer_radius, outer_radius),\
                        fill=material_grey_50_tint))
    dwg.add(dwg.ellipse((width/2.0, height/2.0 + 1),\
                        (outer_radius, outer_radius),\
                        fill=material_grey_50_shade))
    dwg.add(dwg.ellipse((width/2.0, height/2.0), \
                        (outer_radius, outer_radius),\
                        fill=material_grey_50))
                
    # Function
    dwg.add(dwg.path(p, fill='none', stroke=material_red_500, \
                        stroke_linecap='round', stroke_width=stroke))
                        
    # Button gradient
    g = dwg.radialGradient((0.5, 0.5), 0.5, gradientUnits='objectBoundingBox')
    g.add_stop_color((radius - 2.0)/(radius + 1.0), material_red_700_shade)
    g.add_stop_color(1.0, material_red_700_shade, opacity=0.0)
    dwg.defs.add(g)
    
    # Button
    dwg.add(dwg.ellipse((bottom_right[0], height / 2.0 - 1.0), \
                        (radius, radius), \
                         fill=material_red_700_tint))
    dwg.add(dwg.ellipse((bottom_right[0], height / 2.0 + 2.0),
                        (radius + 1.0, radius + 1.0), \
                         fill=g.get_paint_server()))
    dwg.add(dwg.ellipse((bottom_right[0], height / 2.0), (radius, radius), \
                         fill=material_red_700))
                         
    dwg.save()
    return path, 48, 48
    
def make_status_icon(path):
    width = 24
    height = 24
    padding = 2.0
    stroke = 2.0
    radius = 2.5
    top_left = (padding + stroke/2.0, padding + stroke/2.0)
    bottom_right = (width - radius - padding, height - padding - stroke/2.0)
    
    f = SensRecFun(0.5, 3*np.pi, 0.28)
    p = svgpaths.quadratic_fun(f.fun, f.dfun, -1.0, 1.0/3.0, 22, \
                               top_left, bottom_right)
    
    dwg = svgwrite.Drawing(path, size=(width, height), \
                           profile='tiny')
    dwg.add(dwg.path(p, stroke=svgwrite.rgb(100, 100, 100, '%'), fill='none', \
                     stroke_linecap='butt', stroke_width=stroke))
    dwg.add(dwg.ellipse((bottom_right[0], height/2.0), (radius, radius), \
                         fill=svgwrite.rgb(100, 100, 100, '%')))
    dwg.save()
    return path, width, height

export_icon('..\\app\\src\\main\\res\\mipmap', \
            *make_launcher_icon('ic_launcher_sens_rec.svg'), with_xxxhdpi=True)
            
export_icon('..\\app\\src\\main\\res\\drawable', \
            *make_status_icon('ic_stat_sens_rec.svg'))

