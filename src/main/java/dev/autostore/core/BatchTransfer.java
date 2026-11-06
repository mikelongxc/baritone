package dev.autostore.core;

import java.util.*;

/** Verifies one whole-load transaction against a fresh authoritative container snapshot.
 * Keys identify item AND components, not just item ids; -1 is the empty slot. */
public final class BatchTransfer {
    private BatchTransfer() {}
    public record Slot(int key,int count) {}
    public static Policy.Transfer verify(List<Slot> before,List<Slot> after,int chestSize,Set<Integer> clicked) {
        if(before.size()!=after.size() || chestSize<=0 || chestSize>=before.size() || clicked.isEmpty())return Policy.Transfer.INVALID;
        Set<Integer> types=new HashSet<>();
        for(int i:clicked) {
            if(i<chestSize || i>=before.size() || before.get(i).count()<=0)return Policy.Transfer.INVALID;
            types.add(before.get(i).key());
        }
        for(int i=0;i<before.size();i++) {
            Slot old=before.get(i),current=after.get(i);
            if(!valid(old) || !valid(current))return Policy.Transfer.INVALID;
            if(i>=chestSize) {
                if(!clicked.contains(i)) {if(!old.equals(current))return Policy.Transfer.INVALID;}
                else if(current.count()>old.count() || current.count()>0 && current.key()!=old.key())return Policy.Transfer.INVALID;
            } else if((old.count()>0 && !types.contains(old.key()) || current.count()>0 && !types.contains(current.key())) && !old.equals(current))
                return Policy.Transfer.INVALID;
        }
        boolean moved=false;
        for(int key:types) {
            Policy.Transfer transfer=Policy.verify(count(before,chestSize,before.size(),key),count(after,chestSize,after.size(),key),
                count(before,0,chestSize,key),count(after,0,chestSize,key),true);
            if(transfer==Policy.Transfer.INVALID)return transfer;
            moved |= transfer==Policy.Transfer.MOVED;
        }
        return moved?Policy.Transfer.MOVED:Policy.Transfer.NO_CHANGE;
    }
    private static boolean valid(Slot s) {return s.count()>=0 && (s.count()==0?s.key()==-1:s.key()>=0);}
    private static int count(List<Slot> slots,int start,int end,int key) {
        int count=0;for(int i=start;i<end;i++)if(slots.get(i).key()==key)count+=slots.get(i).count();return count;
    }
}
