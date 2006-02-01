package org.antlr.works.grammar;

import edu.usfca.xj.appkit.gview.GView;
import edu.usfca.xj.appkit.gview.object.GElement;
import edu.usfca.xj.appkit.gview.utils.GDOTImporterDOT;
import edu.usfca.xj.appkit.utils.XJAlert;
import org.antlr.works.components.grammar.CEditorGrammar;
import org.antlr.works.editor.EditorMenu;
import org.antlr.works.editor.EditorTab;
import org.antlr.works.menu.ContextualMenuFactory;
import org.antlr.works.prefs.AWPrefs;
import org.antlr.works.syntax.GrammarSyntaxRule;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.*;
/*

[The "BSD licence"]
Copyright (c) 2005-2006 Jean Bovet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
3. The name of the author may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

public abstract class GrammarDOTTab implements Runnable, EditorTab {

    protected CEditorGrammar editor;

    protected JPanel panel;
    protected GView view;

    protected GrammarSyntaxRule rule;

    protected String tempInputFile;
    protected String tempOutputFile;

    protected String error;

    protected GrammarDOTTabDelegate delegate;

    public GrammarDOTTab(CEditorGrammar editor, GrammarDOTTabDelegate delegate) {
        this.editor = editor;
        this.delegate = delegate;
    }

    public Container getContainer() {
        return panel;
    }

    public String getDOTToolPath() {
        return AWPrefs.getDOTToolPath();
    }

    public boolean launch() {
        if(getDOTToolPath() == null) {
            XJAlert.display(editor.getWindowContainer(), "Error", "Cannot generate the graph because the DOT tool path is not defined.\n"+
                    "It can be defined in the Preferences.");
            return false;
        }
        if(!new File(getDOTToolPath()).exists()) {
            XJAlert.display(editor.getWindowContainer(), "Error", "Cannot generate the graph because the DOT tool does not exist at the specified path.\n" +
                    "Check the tool path in the Preferences.");
            return false;
        }

        if(willLaunch()) {
            new Thread(this).start();
            return true;
        } else
            return false;
    }

    protected boolean willLaunch() {
        return true;
    }

    protected boolean checkForCurrentRule() {
        GrammarSyntaxRule rule = editor.getCurrentRule();
        if(rule == null) {
            XJAlert.display(editor.getWindowContainer(), "Error", "The cursor must be inside a rule");
            return false;
        }
        return true;
    }
    
    protected void createInterface(GElement graph) {
        panel = new JPanel(new BorderLayout());

        view = new CustomGView();
        view.setAutoAdjustSize(true);
        view.setRootElement(graph);
        view.setBackground(Color.white);
        view.setDrawBorder(false);

        Box b = Box.createHorizontalBox();
        b.add(new JLabel("Zoom"));
        b.add(createZoomSlider());

        panel.add(b, BorderLayout.NORTH);
        panel.add(new JScrollPane(view), BorderLayout.CENTER);
    }

    protected JSlider createZoomSlider() {
        JSlider slider = new JSlider();
        slider.setFocusable(false);
        slider.setMinimum(1);
        slider.setMaximum(800);
        slider.setValue(100);

        slider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent event) {
                JSlider slider = (JSlider)event.getSource();

                view.setZoom((float)slider.getValue()/100);
                view.repaint();

                // Let the JScrollPane know that the dfaView size may have changed
                view.revalidate();
            }
        });
        return slider;
    }

    public GElement generate() throws Exception {
        generateDOTFile();
        generatePlainTextFile();
        return new GDOTImporterDOT().generateGraph(tempOutputFile);
    }

    protected void generateDOTFile() throws Exception {
    }

    protected void generatePlainTextFile() throws Exception {
        String[] args = new String[] { getDOTToolPath(), "-Tdot", "-o", tempOutputFile, tempInputFile };
        Process p = Runtime.getRuntime().exec(args);

        new StreamWatcher(p.getErrorStream(), "DecisionDFA").start();
        new StreamWatcher(p.getInputStream(), "DecisionDFA").start();

        p.waitFor();
    }

    public abstract void willRun();

    public void run() {
        error = null;

        willRun();

        rule = editor.getCurrentRule();
        
        try {
            tempInputFile = File.createTempFile("GrammarDOTTab", ".in").getAbsolutePath();
            tempOutputFile = File.createTempFile("GrammarDOTTab", ".out").getAbsolutePath();

            createInterface(generate());
        } catch(Exception e) {
            e.printStackTrace();
            error = e.toString();
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                delegate.grammarDOTTabDidComplete(GrammarDOTTab.this, error);
            }
        });

        new File(tempInputFile).delete();
        new File(tempOutputFile).delete();
    }

    public boolean hasExportableGView() {
        return true;
    }

    public GView getExportableGView() {
        return view;
    }

    public Component getTabComponent() {
        return getContainer();
    }

    protected class CustomGView extends GView {

        public JPopupMenu getContextualMenu(GElement element) {
            ContextualMenuFactory factory = new ContextualMenuFactory(editor.editorMenu);
            factory.addItem(EditorMenu.MI_EXPORT_AS_EPS);
            factory.addItem(EditorMenu.MI_EXPORT_AS_IMAGE);
            return factory.menu;
        }

    }

    protected class StreamWatcher extends Thread {

        InputStream is;
        String type;

        public StreamWatcher(InputStream is, String type) {
            this.is = is;
            this.type = type;
        }

        public void run() {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line;
                while ( (line = br.readLine()) != null)
                    editor.console.println(type + ":" + line);
            } catch (IOException e) {
                editor.console.print(e);
            }
        }
    }

    public interface GrammarDOTTabDelegate {
        public void grammarDOTTabDidComplete(GrammarDOTTab tab, String error);
    }

}