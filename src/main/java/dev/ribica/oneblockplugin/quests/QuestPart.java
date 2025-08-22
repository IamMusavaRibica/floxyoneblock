package dev.ribica.oneblockplugin.quests;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bson.Document;


@RequiredArgsConstructor
public abstract class QuestPart {
    private final @Getter Quest quest;
    private @Setter @Getter boolean completed = false;

    public void start0() {
        if (this.completed) {
            throw new IllegalStateException("cannot start a quest part that is already completed.");
        }
        this.onStart();
    }

    public void finish0() {
        this.completed = true;
        this.onComplete();
        quest.continueQuest();
    }

    public Document serialize0() {
        var data = new Document();
        this.serializeInto(data);
        return new Document()
                .append("completed", this.completed)
                .append("data", data);
    }


    public void onStart() {}
    public void onResume() {}
    public void onComplete() {}
    public abstract void serializeInto(Document document);
}
