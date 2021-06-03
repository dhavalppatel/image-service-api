package com.InstagramClone.ImageService;

import java.io.*;
import java.util.*;

import com.InstagramClone.model.Account;
import com.InstagramClone.model.Comment;
import com.InstagramClone.model.Post;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.model.geojson.Position;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.web.server.ResponseStatusException;
import com.mongodb.client.model.geojson.Point;

import javax.servlet.http.HttpSession;

@RestController
public class ImageController {
    private final DatabaseController db = DatabaseController.getInstance();
    private ObjectMapper om = new ObjectMapper();
    private Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
            "cloud_name", "dongodxek",
            "api_key", "473417739651645",
            "api_secret", "C6P529y3ejZcBSeVyqh-4Opeo1w"));

    @PostMapping(value = "/imageupload", produces = "application/json")
    public @ResponseBody String uploadImage(@RequestBody MultipartFile file,
                                            @RequestParam(required = false) String description,
                                            HttpSession session) throws IOException {
        String loggedInAs = (String) session.getAttribute("username");
        if(loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        if(file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "could not accept file");
        }
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());

        return uploadResult.toString();
    }

    @PostMapping(value = "/imagepost", produces = "application/json")
    public @ResponseBody String imagePost(@RequestParam("images") MultipartFile images,
                                          @RequestParam(required = false) String description,
                                          @RequestParam(required = false) String location,
                                          @RequestParam(required = false) String tags,
                                          @RequestParam(required = false) String slongitude,
                                          @RequestParam(required = false) String slatitude,
                                          HttpSession session) throws IOException {
        String username = (String) session.getAttribute("username");
        if(username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        if(images.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "could not accept file");
        }
        Account a = db.getAccount(username);
        Map params = ObjectUtils.asMap("phash", "true",
                                                "image_metadata", "true");
        Map uploadResult = cloudinary.uploader().upload(images.getBytes(), params);
        ArrayList<String> imageList = new ArrayList<>();
        imageList.add((String)uploadResult.get("url"));
        HashMap<String, String> x = (HashMap<String, String>) uploadResult.get("image_metadata");

        String longitudeString = x.get("GPSLongitude");
        String latitudeString = x.get("GPSLatitude");


        String phash = (String)uploadResult.get("phash");
        Post p = new Post(imageList, a._id, a.getUsername(), description);
        if(slongitude != null && slatitude != null) {
            p.setLocation(location);
            p.setGps(new Point(new Position(Double.parseDouble(slongitude),
                    Double.parseDouble(slatitude))));
        } else if(longitudeString != null && latitudeString != null) {
            Double longitude = locationConvert(longitudeString);
            Double latitude = locationConvert(latitudeString);
            System.out.println("Long: "+ longitude + " Lat: " + latitude);
            Point gps = new Point(new Position(longitude, latitude));
            p.setGps(gps);
        }

        if(phash != null) p.setPhash(phash);
        if(tags != null) {
            String[] split = tags.split("\\s*,\\s*");
            ArrayList<String> tagsArray = new ArrayList<>(Arrays.asList(split));
            p.setTags(tagsArray);
        }
        db.insertPost(p);
        ObjectNode response = om.createObjectNode();
        response.put("status", "success");
        response.put("url", (String)uploadResult.get("url"));
        return om.writeValueAsString(response);
    }

    public Double locationConvert(String loc) {
        loc = loc.replaceAll("[A-Za-z'\\\"]", "");
        StringTokenizer st = new StringTokenizer(loc);
        int count = 0;
        Double result = 0.0d;
        while(st.hasMoreTokens()){
            Double lo;
            try{
                lo = Double.parseDouble(st.nextToken());
                count++;
            } catch (NumberFormatException e) {
                continue;
            }
            if(count == 1) {
                result += lo;
            } else if(count == 2) {
                result += lo / 60.0f;
            } else if(count == 3) {
                result += lo / 3600.0f;
            }
        }
        return result;
    }

    @PostMapping(value = "/imagesearch", produces = "application/json")
    public @ResponseBody String imageSearch(@RequestParam MultipartFile image,
                                            HttpSession session) throws Exception {
        String username = (String) session.getAttribute("username");
        if(username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        if(image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "could not accept file");
        }
        Account a = db.getAccount(username);
        Map params = ObjectUtils.asMap("phash", "true");
        Map uploadResult = cloudinary.uploader().upload(image.getBytes(), params);
        String phash = (String)uploadResult.get("phash");
        String id = (String)uploadResult.get("public_id");
        cloudinary.api().deleteResources(Arrays.asList(id), null);
        ObjectNode response = om.createObjectNode();
        response.putPOJO("post", db.imageSearch(phash, username));
        return om.writeValueAsString(response);
    }

    @PostMapping(value = "/duplicateimagesearch", produces = "application/json")
    public @ResponseBody ArrayList<Post> duplicateImageSearch(@RequestParam MultipartFile image,
                                            HttpSession session) throws Exception {
        String username = (String) session.getAttribute("username");
        if(username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        if(image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "could not accept file");
        }
        Account a = db.getAccount(username);
        Map params = ObjectUtils.asMap("phash", "true");
        Map uploadResult = cloudinary.uploader().upload(image.getBytes(), params);
        String phash = (String)uploadResult.get("phash");
        String id = (String)uploadResult.get("public_id");
        cloudinary.api().deleteResources(Arrays.asList(id), null);
        return db.duplicateImageSearch(phash, username);
    }

    @PostMapping(value = "/likepost", produces = "application/json")
    public @ResponseBody String likePost(@RequestParam String postid,
                                         HttpSession session) throws JsonProcessingException {
        String loggedInAs = (String) session.getAttribute("username");
        if(loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Post p;
        Account a;
        try {
            p = db.getPost(new ObjectId(postid));
            a = db.getAccount(loggedInAs);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid object id");
        }
        if(p == null || a == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid request");
        } else {
            if(db.likePost(a, p)) {
                ObjectNode response = om.createObjectNode();
                response.put("_id", p.get_id().toHexString());
                response.put("likes", p.getLikes() + 1);
                return om.writeValueAsString(response);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already liked");
            }
        }
    }

    @PostMapping(value = "/unlikepost", produces = "application/json")
    public @ResponseBody String unlikePost(@RequestParam String postid,
                                         HttpSession session) throws JsonProcessingException {
        String loggedInAs = (String) session.getAttribute("username");
        if(loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Post p;
        Account a;
        try {
            p = db.getPost(new ObjectId(postid));
            a = db.getAccount(loggedInAs);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid object id");
        }
        if(p == null || a == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid request");
        } else {
            if (db.unlikePost(a, p)) {
                ObjectNode response = om.createObjectNode();
                response.put("_id", p.get_id().toHexString());
                response.put("likes", p.getLikes() - 1);
                return om.writeValueAsString(response);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already unliked");
            }
        }
    }

    @GetMapping(value = "/imagemap", produces = "application/json")
    public @ResponseBody String imageMap(@RequestParam String username,
                                         HttpSession session) throws JsonProcessingException {
        String loggedInAs = (String) session.getAttribute("username");
        if(loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Account a = db.getAccount(username);
        if(a == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account");
        }
        ArrayList<ObjectId> posts = a.getPosts();
        String url = "https://image.maps.api.here.com/mia/1.6/mapview?app_id=1blySexZd7OY515FRAlM&app_code=y8TY2X31NxrTG8yqWloxJg&w=1000&h=600&poithm=1&poitxs=16&poi=";
        for (ObjectId postid : posts) {
            Post p = db.getPost(postid);
            Point gps = p.getGps();
            if(gps != null) {
                url += gps.getPosition().getValues().get(1) + "," +
                        gps.getPosition().getValues().get(0) + ",";
            }
        }
        if(url.endsWith(",")) {
            url = url.substring(0, url.length()-1);
        }
        ObjectNode response = om.createObjectNode();
        response.put("url", url);
        return om.writeValueAsString(response);
    }

    @PostMapping(value = "/liketoggle", produces = "application/json")
    public @ResponseBody String likeTogglePost(@RequestParam String postid,
                                         HttpSession session) throws JsonProcessingException {
        String loggedInAs = (String) session.getAttribute("username");
        if(loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Post p;
        Account a;
        try {
            p = db.getPost(new ObjectId(postid));
            a = db.getAccount(loggedInAs);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid object id");
        }
        if(p == null || a == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid request");
        } else {
            ArrayList<ObjectId> likedPosts = a.getLikedPosts();
            if(!likedPosts.contains(p._id)) {
                db.likePost(a, p);
                ObjectNode response = om.createObjectNode();
                response.put("_id", p.get_id().toHexString());
                response.put("liked", "true");
                response.put("likes", p.getLikes() + 1);
                return om.writeValueAsString(response);
            } else {
                db.unlikePost(a, p);
                ObjectNode response = om.createObjectNode();
                response.put("_id", p.get_id().toHexString());
                response.put("liked", "false");
                response.put("likes", p.getLikes() - 1);
                return om.writeValueAsString(response);
            }
        }
    }

    @GetMapping(value = "/isliked", produces = "application/json")
    public @ResponseBody String isLiked(@RequestParam String postid, @RequestParam String username) throws JsonProcessingException {
        Account a;
        ObjectId p;
        try {
            a = db.getAccount(username);
            p = new ObjectId(postid);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid object id");
        }
        if (a == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid request");

        ArrayList<ObjectId> likedPosts = a.getLikedPosts();
        ObjectNode response = om.createObjectNode();
        response.put("_id", p.toHexString());

        if (likedPosts.contains(p))
            response.put("liked", "true");
        else
            response.put("liked", "false");

        return om.writeValueAsString(response);
    }

    @PostMapping(value = "/writecomment", produces = "application/json")
    public @ResponseBody String writeComment(@RequestParam String postid,
                                             @RequestParam(required = false) String comment,
                                             @RequestParam(required = false) MultipartFile image,
                                             HttpSession session) throws IOException {
        String username = (String) session.getAttribute("username");
        System.out.println("WRITE COMMENT RECIEVED");
        if(username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Post p;
        Account a;
        try {
            p = db.getPost(new ObjectId(postid));
            a = db.getAccount(username);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid post or account");
        }

        if(p == null || a == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid request");
        } else {
            if(image != null) {
                Map uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.emptyMap());
                String imageUrl = (String) uploadResult.get("url");
                if(comment == null) comment = "";
                db.writeComment(a, p, comment, imageUrl);
            } else {
                if(comment == null) comment = "";
                db.writeComment(a, p, comment);
            }
            ObjectMapper om = new ObjectMapper();
            ObjectNode response = om.createObjectNode();
            response.put("status", "success");
            return om.writeValueAsString(response);
        }
    }

    @PostMapping(value = "/writecommentimage", produces = "application/json")
    public @ResponseBody String writeCommentImage(@RequestParam String postid,
                                             @RequestParam MultipartFile image,
                                             HttpSession session) throws IOException {
        String username = (String) session.getAttribute("username");
        System.out.println("IMAGE COMMENT RECIEVED");
        if(username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Post p;
        Account a;
        try {
            p = db.getPost(new ObjectId(postid));
            a = db.getAccount(username);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid post or account");
        }

        if(p == null || a == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid request");
        } else {
            Map uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.emptyMap());
            String imageUrl = (String) uploadResult.get("url");
            db.writeComment(a, p, "", imageUrl);
            ObjectMapper om = new ObjectMapper();
            ObjectNode response = om.createObjectNode();
            response.put("status", "success");
            return om.writeValueAsString(response);
        }
    }




    @GetMapping(value = "/getcommentsfrompost", produces = "application/json")
    public @ResponseBody ArrayList<Comment> getCommentsFromPost(@RequestParam String postid) {
        Post p;
        try {
            p = db.getPost(new ObjectId(postid));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid post or account");
        }

        if(p == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid request");
        } else {
            return p.getComments();
        }
    }



    @GetMapping(value = "/getpost", produces = "application/json")
    public @ResponseBody String getPost(@RequestParam String postid)
            throws JsonProcessingException {
        Post p;
        try {
            p = db.getPost(new ObjectId(postid));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid post or account");
        }

        if(p == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid request");
        } else {
            ObjectNode response = om.createObjectNode();
            response.put("postid", p.get_id().toHexString());
            response.putPOJO("images", p.getImageId());
            response.put("accountid", p.getAccount().toHexString());
            response.put("description", p.getDescription());
            response.put("location", p.getLocation());
            response.put("likes", p.getLikes());
            response.putPOJO("tags", p.getTags());
            response.put("date", p.getDate().toString());
            return om.writeValueAsString(response);
        }
    }

    @PostMapping(value = "/uploadprofilepicture", produces = "application/json")
    public @ResponseBody String imagePost(@RequestParam("images") MultipartFile images,
                                          HttpSession session) throws IOException {
        String username = (String) session.getAttribute("username");
        if(username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        if(images.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "could not accept file");
        }
        Account a = db.getAccount(username);
        Map uploadResult = cloudinary.uploader().upload(images.getBytes(), ObjectUtils.emptyMap());
        String url = (String) uploadResult.get("url");

        db.setProfilePicture(a._id, url);
        ObjectNode response = om.createObjectNode();
        response.put("status", "success");
        response.put("url", url);
        return om.writeValueAsString(response);
    }
}