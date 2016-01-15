# -*- coding: utf-8 -*-
"""
   (C) Copyright 2015, 2016 Wojciech Mruczkiewicz
   
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
import svgcolors
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
    

def make_launcher_icon(path, with_background=True):
    colors = svgcolors.build_material_colors()
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
    if with_background:
        bg = dwg.radialGradient((0.5, 0.5), 0.5, \
                                gradientUnits='objectBoundingBox')
        bg.add_stop_color((outer_radius - 3.0)/(outer_radius + 6.0), \
                          colors['grey_400_shade'])
        bg.add_stop_color(1.0, colors['grey_400_shade'], opacity=0.0)
        dwg.defs.add(bg)
#        dwg.add(dwg.ellipse((width/2.0, height/2.0 - 1), \
#                            (outer_radius, outer_radius),\
#                            fill=colors['grey_50_tint']))
        dwg.add(dwg.ellipse((width/2.0, height/2.0 + 3.0),\
                            (outer_radius + 4.0, outer_radius + 6.0),\
                            fill=bg.get_paint_server()))
        dwg.add(dwg.ellipse((width/2.0, height/2.0), \
                            (outer_radius, outer_radius),\
                            fill=colors['grey_50']))
                
    # Function
    dwg.add(dwg.path(p, fill='none', stroke=colors['red_500'], \
                        stroke_linecap='round', stroke_width=stroke))
                        
    # Button gradient
    bg = dwg.radialGradient((0.5, 0.5), 0.5, gradientUnits='objectBoundingBox')
    bg.add_stop_color((radius - 2.0)/(radius + 1.0), colors['lime_500_shade'])
    bg.add_stop_color(1.0, colors['lime_500_shade'], opacity=0.0)
    dwg.defs.add(bg)
    
    # Button
    dwg.add(dwg.ellipse((bottom_right[0], height / 2.0 - 1.0), \
                        (radius, radius), \
                         fill=colors['lime_500_tint']))
    dwg.add(dwg.ellipse((bottom_right[0], height / 2.0 + 2.0),
                        (radius + 1.0, radius + 1.0), \
                         fill=bg.get_paint_server()))
    dwg.add(dwg.ellipse((bottom_right[0], height / 2.0), (radius, radius), \
                         fill=colors['lime_500']))
                         
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

launcher_icon = make_launcher_icon('ic_launcher_sens_rec.svg', \
                                   with_background=True)
export_icon('..\\app\\src\\main\\res\\mipmap', *launcher_icon, \
            with_xxxhdpi=True)
            
#status_icon = make_status_icon('ic_stat_sens_rec.svg');
#export_icon('..\\app\\src\\main\\res\\drawable', *status_icon)

