package com.ctrip.framework.apollo.common.dto;

import java.util.List;

public class DingTalkMessageDTO {

    private String msgtype;
    private Text text;

    private ActionCard actionCard;

    private At at;

    private Markdown markdown;

    public String getMsgtype() {
        return msgtype;
    }

    public void setMsgtype(String msgtype) {
        this.msgtype = msgtype;
    }

    public Text getText() {
        return text;
    }

    public void setText(Text text) {
        this.text = text;
    }

    public ActionCard getActionCard() {
        return actionCard;
    }

    public void setActionCard(ActionCard actionCard) {
        this.actionCard = actionCard;
    }

    public At getAt() {
        return at;
    }

    public void setAt(At at) {
        this.at = at;
    }

    public Markdown getMarkdown() {
        return markdown;
    }

    public void setMarkdown(Markdown markdown) {
        this.markdown = markdown;
    }

    public DingTalkMessageDTO() {
    }

    public static class Markdown {
        private String title;
        private String text;

        // 对应的 getter 和 setter
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static class ActionCard {
        private String title;
        private String text;
        private String actionTitle1;
        private String actionURL1;
        //private String actionTitle1;

        // 对应的 getter 和 setter
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getActionTitle1() {
            return actionTitle1;
        }

        public void setActionTitle1(String actionTitle1) {
            this.actionTitle1 = actionTitle1;
        }

        public String getActionURL1() {
            return actionURL1;
        }

        public void setActionURL1(String actionURL1) {
            this.actionURL1 = actionURL1;
        }
    }

    public static class Text {
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public static class At {
        private List<String> atUserIds;

        public List<String> getAtUserIds() {
            return atUserIds;
        }

        public void setAtUserIds(List<String> atUserIds) {
            this.atUserIds = atUserIds;
        }
    }
}
