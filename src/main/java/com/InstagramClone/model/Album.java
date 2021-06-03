package com.InstagramClone.model;
import java.util.ArrayList;
import java.util.Date;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.bson.types.ObjectId;

public class Album implements Comparable<Post> {
    @JsonSerialize(using = ToStringSerializer.class)
    public ObjectId _id;
    public String name;
    public ArrayList<String> images;
    @JsonSerialize(using = ToStringSerializer.class)
    public ObjectId creator;
    @JsonSerialize(using = ToStringSerializer.class)
    public ArrayList<ObjectId> group;
    public String creatorUsername;
    @JsonSerialize(using = ToStringSerializer.class)
    public Date date;

    public Album(ObjectId account, String username , String name) {
        this.set_id(new ObjectId());
        this.images = new ArrayList<>();
        this.creator = account;
        this.creatorUsername = username;
        this.group = new ArrayList<>();
        this.name = name;
        this.setDate(new Date());
    }

    public Album() {

    }

    public ObjectId get_id() {
        return _id;
    }

    public void set_id(ObjectId _id) {
        this._id = _id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArrayList<String> getImages() {
        return images;
    }

    public void setImages(ArrayList<String> images) {
        this.images = images;
    }

    public ObjectId getCreator() {
        return creator;
    }

    public void setCreator(ObjectId creator) {
        this.creator = creator;
    }

    public ArrayList<ObjectId> getGroup() {
        return group;
    }

    public void setGroup(ArrayList<ObjectId> group) {
        this.group = group;
    }

    public String getCreatorUsername() {
        return creatorUsername;
    }

    public void setCreatorUsername(String creatorUsername) {
        this.creatorUsername = creatorUsername;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @Override
    public int compareTo(Post post) {
        return this.date.compareTo(post.date);
    }
}

