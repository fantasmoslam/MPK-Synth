/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth;

import android.widget.FrameLayout;
import org.paleotone.synth.ui.UiState;

/**
 * The round-three interface over the whole screen. Until phase 2 redraws them, the
 * existing Mixer, Scape, Library and firmware pages are shown inside its content area.
 */
final class Shell extends FrameLayout {
    final PhosphorView view;
    private final Workspace legacy;
    private int shownTab=-1,left,top,width,height;

    Shell(MainActivity activity,Workspace workspace,PhosphorView phosphor){
        super(activity);view=phosphor;legacy=workspace;
        legacy.embedded();
        addView(view,new LayoutParams(-1,-1));
        addView(legacy,new LayoutParams(0,0));legacy.setVisibility(GONE);
        view.listener=this::placeLegacy;
    }
    private void placeLegacy(){
        UiState.Screen hosted=view.screens.hosted;float[] r=view.screens.hostRect;
        if(hosted==null||r==null){
            if(legacy.getVisibility()!=GONE){legacy.panel.release();legacy.setVisibility(GONE);shownTab=-1;}
            return;
        }
        int tab=hosted==UiState.Screen.LEGACY?0:hosted==UiState.Screen.LIBRARY?3:view.screens.mixerPage()==0?1:2;
        float s=view.scale();
        int l=Math.round(r[0]*s),t=Math.round(r[1]*s),w=Math.round(r[2]*s),h=Math.round(r[3]*s);
        if(l!=left||t!=top||w!=width||h!=height){
            left=l;top=t;width=w;height=h;
            LayoutParams p=new LayoutParams(w,h);p.leftMargin=l;p.topMargin=t;legacy.setLayoutParams(p);
        }
        if(tab!=shownTab){shownTab=tab;legacy.show(tab);}
        if(legacy.getVisibility()!=VISIBLE)legacy.setVisibility(VISIBLE);
    }
}
