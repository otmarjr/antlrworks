package org.antlr.works.debugger;

import org.antlr.runtime.Token;

import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

/*

[The "BSD licence"]
Copyright (c) 2004-05 Jean Bovet
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

public class DebuggerPlayer {

    protected Debugger debugger;

    protected Stack lookAheadTextStack;

    protected LookAheadText lastLookAheadText;
    protected DebuggerInputText inputText;

    protected int resyncing = 0;

    public DebuggerPlayer(Debugger debugger) {
        this.debugger = debugger;
        inputText = new DebuggerInputText(debugger.textPane);
        lookAheadTextStack = new Stack();
    }

    public void pushLookAheadText(Object id) {
        rewindLookAheadText();
        lookAheadTextStack.push(new LookAheadText(id));
    }

    public void popLookAheadText(Object id) {
        if(lookAheadTextStack.empty()) {
            System.err.println("Lookahead text stack is empty while trying to popup object id "+id);
            return;
        }

        LookAheadText lat = (LookAheadText)lookAheadTextStack.peek();
        if(lat.id.equals(id)) {
            lastLookAheadText = (LookAheadText)lookAheadTextStack.pop();
        } else
            System.err.println("The top-of-stack LookAheadText doesn't correspond to id "+id+" ("+lat.id+")");
    }

    public void rewindLookAheadText() {
        if(lastLookAheadText != null) {
            if(lastLookAheadText.enable) {
                lastLookAheadText.rewind();
                lastLookAheadText.disable();
            }
            lastLookAheadText = null;
        }
    }

    public LookAheadText getLookAheadText() {
        if(lookAheadTextStack.empty())
            return null;
        else
            return (LookAheadText)lookAheadTextStack.peek();
    }

    public synchronized void resetPlayEvents() {
        debugger.resetGUI();

        inputText.reset();

        lastLookAheadText = null;
        lookAheadTextStack.clear();

        resyncing = 0;
    }

    public void playEvents(List events) {
        resetPlayEvents();

        Iterator iterator = events.iterator();
        while(iterator.hasNext()) {
            try {
                playEvent((DebuggerEvent)iterator.next(), !iterator.hasNext());
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void playEvent(DebuggerEvent event, boolean lastEvent) {
        debugger.eventListModel.addElement(event);
        debugger.selectLastInfoListItem();

        switch(event.type) {
            case DebuggerEvent.ENTER_RULE:
                playEnterRule(event.s);
                break;

            case DebuggerEvent.EXIT_RULE:
                playExitRule(event.s);
                break;

            case DebuggerEvent.ENTER_SUBRULE:
                playEnterSubrule(event.int1);
                break;

            case DebuggerEvent.EXIT_SUBRULE:
                playExitSubrule(event.int1);
                break;

            case DebuggerEvent.ENTER_DECISION:
                playEnterDecision(event.int1);
                break;

            case DebuggerEvent.EXIT_DECISION:
                playExitDecision(event.int1);
                break;

            case DebuggerEvent.ENTER_ALT:
                playEnterAlt(event.int1);
                break;

            case DebuggerEvent.LT:
                playLT(event.int1, event.token);
                break;

            case DebuggerEvent.CONSUME_TOKEN:
                playConsumeToken(event.token, false);
                break;

            case DebuggerEvent.CONSUME_HIDDEN_TOKEN:
                playConsumeToken(event.token, true);
                break;

            case DebuggerEvent.LOCATION:
                playLocation(event.int1, event.int2);
                break;

            case DebuggerEvent.MARK:
                playMark(event.int1);
                break;

            case DebuggerEvent.REWIND:
                playRewind(event.int1);
                break;

            case DebuggerEvent.RECOGNITION_EXCEPTION:
                playRecognitionException(event.exception);
                break;

            case DebuggerEvent.BEGIN_RESYNC:
                playBeginResync();
                break;

            case DebuggerEvent.END_RESYNC:
                playEndResync();
                break;

            case DebuggerEvent.TERMINATE:
                if(lookAheadTextStack.size() > 0) {
                    System.err.println("Lookahead text stack not empty");
                }
                break;
        }

        if(lastEvent) {
            playLocation();
        }
    }

    public void playEnterRule(String ruleName) {
        debugger.pushRule(ruleName);
        rewindLookAheadText();
    }

    public void playExitRule(String ruleName) {
        debugger.popRule(ruleName);
        rewindLookAheadText();
    }

    public void playEnterSubrule(int i) {
        rewindLookAheadText();
    }

    public void playExitSubrule(int i) {
        rewindLookAheadText();
    }

    public void playEnterDecision(int i) {
        pushLookAheadText(new Integer(i));
    }

    public void playExitDecision(int i) {
        popLookAheadText(new Integer(i));
    }

    public void playEnterAlt(int alt) {
    }

    public void playLT(int index, Token token) {
        if(getLookAheadText() != null) {
            getLookAheadText().LT(index, token);
            return;
        }
    }

    public void playConsumeToken(Token token, boolean hidden) {
        if(resyncing>0) {
            rewindLookAheadText();
            inputText.consumeToken(token, DebuggerInputText.TOKEN_DEAD);
            return;
        }

        if(getLookAheadText() != null) {
            getLookAheadText().consumeToken(token, hidden);
            return;
        }

        // Parse tree construction
        if(!hidden) {
            debugger.addToken(token);
        }

        rewindLookAheadText();
        inputText.consumeToken(token, hidden?DebuggerInputText.TOKEN_HIDDEN:DebuggerInputText.TOKEN_NORMAL);
    }

    protected int lastLocationLine;
    protected int lastLocationPos;

    public void playLocation(int line, int pos) {
        // Remember the last position in order to display
        // it when the events are all consumed. This allows
        // to remove the fast back/forth move of the cursor
        // in the grammar (not needed)
        lastLocationLine = line;
        lastLocationPos = pos;
    }

    public void playLocation() {
        debugger.restorePreviousGrammarAttributeSet();

        int index = debugger.computeAbsoluteGrammarIndex(lastLocationLine, lastLocationPos);
        if(index < 0)
            return;

        try {
            debugger.editor.setCaretPosition(index);
            debugger.storeGrammarAttributeSet(index);

            // Select one character to be underlined
            debugger.editor.getTextPane().moveCaretPosition(index+1);

            StyleContext sc = StyleContext.getDefaultStyleContext();
            AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Background, Color.red);
            debugger.editor.getTextPane().setCharacterAttributes(aset, false);

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void playMark(int id) {
        if(getLookAheadText() != null) {
            getLookAheadText().setEventMark(id);
            return;
        }
    }

    public void playRewind(int id) {
        if(getLookAheadText() != null) {
            getLookAheadText().setEventRewind(id);
            return;
        }
    }

    public void playRecognitionException(Exception e) {
        debugger.addException(e);
    }

    public void playBeginResync() {
        resyncing++;
    }

    public void playEndResync() {
        resyncing--;
    }

    protected class LookAheadText {

        public int start;
        public Object id;

        protected int startLTIndex;
        protected java.util.List ltTokens;

        protected boolean enable;
        protected boolean mark;

        public LookAheadText(Object id) {
            this.start = inputText.cursorIndex;
            this.id = id;

            startLTIndex = -1;
            ltTokens = new ArrayList();
            enable = false;
            mark = false;

            enable();
        }

        public void enable() {
            if(enable)
                System.err.println("Enabling an already enabled LookAheadText");

            enable = true;
        }

        public void disable() {
            if(!enable)
                System.err.println("Disabling an already disabled LookAheadText");

            enable = false;
        }

        public boolean existsToken(Token token) {
            for (Iterator iterator = ltTokens.iterator(); iterator.hasNext();) {
                Token t = (Token) iterator.next();
                if(t.getTokenIndex() == token.getTokenIndex())
                    return true;
            }
            return false;
        }

        public void consumeToken(Token token, boolean hidden) {
            if(!mark)
                inputText.consumeToken(token, hidden?DebuggerInputText.TOKEN_HIDDEN:DebuggerInputText.TOKEN_NORMAL);
        }

        public void LT(int index, Token token) {
            if(!enable)
                return;

            // @todo what if between mark and rewind ? Use token index instead ?
            // Rewind when LT 1 and only if not between mark/rewind events
            if(index == 1 && !mark)
                rewind();

            inputText.doLT(token);
        }

        public void setEventMark(int id) {
            mark = true;
        }

        public void setEventRewind(int id) {
            mark = false;
        }

        public void rewind() {
            inputText.rewind(start);
        }
    }

}