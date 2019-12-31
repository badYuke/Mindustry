package mindustry.entities.type.base;

import arc.*;
import arc.math.*;
import arc.util.*;
import mindustry.ui.*;
import mindustry.type.*;
import mindustry.world.*;
import arc.graphics.g2d.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.graphics.*;
import arc.scene.ui.layout.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.entities.Effects.*;
import mindustry.world.blocks.distribution.CompressedConveyor.*;

import static mindustry.Vars.*;

public class CraterUnit extends GroundUnit{
    private final Effect io = Fx.plasticburn; // effect to play when poofing in and out of existence
    private int inactivity = 0;

    private final UnitState

    load = new UnitState(){
        public void update(){
            // launch when crater is full         || launch when it got bumped   || launch when idling
            if(item().amount >= getItemCapacity() || !velocity.isZero(1f) || inactivity++ > 120) state.set(move);
        }
    },
    move = new UnitState(){
        public void update(){

            // switch to unload when on an end tile
            if(dst(on()) < 1.5f && on(Track.end)){
                state.set(unload);
                return;
            }

            // compute a destination
            Tile target = on().front();
            if(!dibs(target)) target = on();

            if(dst(target) > 1f){ // move to target...
                velocity.add(vec.trnsExact(angleTo(target), type.speed * Time.delta()));
            }else{ // ...but snap on its center
                set(target.drawx(), target.drawy()); // fixme: make movement more majestically
            }

            rotation = Mathf.slerpDelta(rotation, angleTo(on().front()), type.rotatespeed);
        }
    },
    unload = new UnitState(){
        public void update(){

            /*
              Switch back to moving when:
              - some unit bumped it off
              - track got extended
             */
            if(!on(Track.end)){
                state.set(move);
                return;
            }

            if(item.amount == 0) return; // update will take care of poofing
            if(on().block().offloadDir(on(), item.item)) item.amount--;
        }
    };

    // ensures (well, tries to make sure) that each crater has one tile to itself
    private boolean dibs(Tile tile){
        if(!tile.block().compressable) return false;

        // invalidate existing dibs
        if(Track.dibs.containsKey(tile)){
            if(Track.dibs.get(tile).isDead() || Track.dibs.get(tile).dst(tile) > tilesize) Track.dibs.remove(tile);
        }

        // instantly claim unclaimed tiles
        if(!Track.dibs.containsKey(tile)){
            Track.dibs.remove(on());
            Track.dibs.put(tile, this);
            return true;
        }

        // tile is claimed by current unit
        if(Track.dibs.get(tile) == this){
            return true;
        }

        // various reasons
        return false;
    }

    @Override
    public UnitState getStartState(){
        return load;
    }

    @Override
    public void drawStats(){
        drawBackItems();
        drawLight();
    }

    @Override
    public void update(){
        super.update();

        // in the void  || not on a valid track       || is empty
        if(on() == null || !on().block().compressable || item.amount == 0){
            Effects.effect(io, x, y); // poof out of existence
            kill();
        }
    }

    @Override
    public void added(){
        super.added();
        Effects.effect(io, x, y); // poof in to existence
        baseRotation = rotation; // needed to prevent wobble: load > move
    }

    @Override
    public void onDeath(){
        Events.fire(new UnitDestroyEvent(this)); // prevent deathrattle (explosion/sound/etc)
    }

    @Override
    public boolean isCommanded(){
        return false; // it has its own logic
    }

    public static CraterUnit on(Tile tile){ // summons a crater on said tile
        CraterUnit crater = (CraterUnit)UnitTypes.crater.create(tile.getTeam());
        crater.set(tile.drawx(), tile.drawy());
        crater.rotation = tile.rotation() * 90;
        crater.add();
        Events.fire(new UnitCreateEvent(crater));
        return crater;
    }

    public boolean on(Track track){
        return track.check.get(on());
    }

    public Tile on(){
        return world.ltileWorld(x, y);
    }

    private void drawBackItems(){
        if(item.amount == 0) return;

        float itemtime = 0.5f;
        float backTrns = 0f;

        float size = itemSize / 1.5f;

        Draw.rect(item.item.icon(Cicon.medium),
        x + Angles.trnsx(rotation + 180f, backTrns),
        y + Angles.trnsy(rotation + 180f, backTrns),
        size, size, rotation);

        Fonts.outline.draw(item.amount + "",
        x + Angles.trnsx(rotation + 180f, backTrns),
        y + Angles.trnsy(rotation + 180f, backTrns) - 1,
        Pal.accent, 0.25f * itemtime / Scl.scl(1f), false, Align.center);

        Draw.reset();
    }

    public boolean loading(){
        return state.is(load);
    }

    public boolean acceptItem(Item item){
        if(this.item.amount > 0 && this.item.item != item) return false;
        if(this.item.amount >= getItemCapacity()) return false;

//        return on(Track.start);
        return true;
    }

    public void handleItem(Item item){
        this.item.item = item;
        this.inactivity = 0;
        this.item.amount++;
    }

    /**
     * Since normal conveyors get faster when boosted,
     * this piece of code changes their capacity,
     * make sure capacity is dividable by 4,
     * for the best user experience.
     */
    @Override
    public int getItemCapacity(){
        if(on() == null || on().entity == null) return type.itemCapacity;

        return Mathf.round(type.itemCapacity * on().entity.timeScale);
    }
}
