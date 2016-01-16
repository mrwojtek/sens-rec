# -*- coding: utf-8 -*-
"""
   (C) Copyright 2015, 2106 Wojciech Mruczkiewicz
   
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

import svgwrite


def build_material_colors():
    
    def color_to_svg_rgb(r, g, b):
        return svgwrite.rgb(r, g, b, 'RGB')
    
    def append_color(name, color, tint, tint_weight, shade, shade_weight):
        colors[name] = color_to_svg_rgb(*color)

        tint_weight_inv = 1.0 - tint_weight        
        colors[name + '_tint'] = color_to_svg_rgb(\
            int(color[0] * tint_weight_inv + tint[0] * tint_weight),\
            int(color[1] * tint_weight_inv + tint[1] * tint_weight),\
            int(color[2] * tint_weight_inv + tint[2] * tint_weight))
        
        shade_weight_inv = 1.0 - shade_weight
        colors[name + '_shade'] = color_to_svg_rgb(\
            int(color[0] * shade_weight_inv + shade[0] * shade_weight),\
            int(color[1] * shade_weight_inv + shade[1] * shade_weight),\
            int(color[2] * shade_weight_inv + shade[2] * shade_weight))
    
    white = (255, 255, 255)
    grey_900 = (33,33,33)
    brown_900 = (62,39,35)
    blue_grey_900 = (38,50,56)
    
    colors = {}
    append_color('grey_50', (250, 250, 250), white, 0.4, grey_900, 0.1)
    append_color('grey_100', (245, 245, 245), white, 0.4, grey_900, 0.1)
    append_color('grey_200', (238, 238, 238), white, 0.4, grey_900, 0.1)
    append_color('grey_300', (224, 224, 224), white, 0.4, grey_900, 0.1)
    append_color('grey_400', (189, 189, 189), white, 0.2, grey_900, 0.2)
    append_color('grey_500', (158, 158, 158), white, 0.2, grey_900, 0.2)
    append_color('red_300', (229, 115, 115), white, 0.2, brown_900, 0.2)
    append_color('red_400', (239, 83, 80), white, 0.2, brown_900, 0.2)
    append_color('red_500', (244, 67, 54), white, 0.2, brown_900, 0.2)
    append_color('red_600', (229, 57, 57), white, 0.2, brown_900, 0.2)
    append_color('red_700', (211, 47, 47), white, 0.2, brown_900, 0.2)
    append_color('red_800', (198, 40, 40), white, 0.2, brown_900, 0.2)
    append_color('red_900', (183, 28, 28), white, 0.2, brown_900, 0.2)
    append_color('lime_500', (205, 220, 57), white, 0.2, blue_grey_900, 0.2)
    append_color('lime_700', (175, 180, 43), white, 0.2, blue_grey_900, 0.2)
    
    return colors