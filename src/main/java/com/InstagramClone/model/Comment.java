package com.InstagramClone.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.bson.types.ObjectId;

import java.util.Date;

public final class Comment {
    private String username;
    @JsonSerialize(using = ToStringSerializer.class)
    private ObjectId userid;
    private String comment;
    @JsonSerialize(using = ToStringSerializer.class)
    private Date date;
    private String image;

    public Comment(String username, ObjectId userid, String comment) {
        this.username = username;
        this.userid = userid;
        this.comment = comment;
        this.date = new Date();
        this.image = "";
    }

    public Comment(String username, ObjectId userid, String comment, String imageUrl) {
        this.username = username;
        this.userid = userid;
        this.comment = comment;
        this.date = new Date();
        this.image = imageUrl;
    }

    public Comment() {

    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public ObjectId getUserid() {
        return userid;
    }

    public void setUserid(ObjectId userid) {
        this.userid = userid;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
