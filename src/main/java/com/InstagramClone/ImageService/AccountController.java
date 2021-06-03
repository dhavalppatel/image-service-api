package com.InstagramClone.ImageService;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import javax.servlet.http.HttpSession;

import com.InstagramClone.model.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController
public class AccountController {
    private ObjectMapper om = new ObjectMapper();
    private final DatabaseController db = DatabaseController.getInstance();
    private Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
            "cloud_name", "dongodxek",
            "api_key", "473417739651645",
            "api_secret", "C6P529y3ejZcBSeVyqh-4Opeo1w"));

    // Creates account given a username and password
    @PostMapping(value = "/signup", produces = "application/json")
    public String signUp(@RequestParam String username, @RequestParam String password) throws JsonProcessingException, NoSuchAlgorithmException {
        ObjectNode response = om.createObjectNode();

        if(username.equals("")){
            response.put("username", "Username is required");
        }
        if(password.equals("")){
            response.put("password", "Password is required");
        }
        if(response.size()>0) return om.writeValueAsString(response);

        if(username == null || password == null){
            response.put("status", "failed");
            response.put("error", "Please enter both a username and password");
            return om.writeValueAsString(response);
        }
        Account account = new Account(username, password);
        if (db.getAccount(username) != null) {
            response.put("status", "failed");
            response.put("error", "Account already exists with username " + username);
            return om.writeValueAsString(response);
        } else {
            String id = db.createAccount(account);
            response.put("status", "success");
            response.put("username", username);
            response.put("_id", id);
            return om.writeValueAsString(response);
        }
    }

    @PostMapping(value = "/signin", produces = "application/json")
    public String signIn(@RequestParam String username, @RequestParam String password, HttpSession session) throws JsonProcessingException, NoSuchAlgorithmException {
        if(username == null || password == null){
            ObjectNode response = om.createObjectNode();
            response.put("status", "failed");
            response.put("error", "Please enter both a username and password");
            return om.writeValueAsString(response);
        }
        String loggedInAs = (String) session.getAttribute("Username");

        ObjectNode response = om.createObjectNode();

        if (loggedInAs == null) {
            Account loggedInAccount = db.checkAccount(username, password);
            if (loggedInAccount != null) {
                session.setAttribute("username", loggedInAccount.getUsername());
                session.setAttribute("userid", loggedInAccount.get_id());
                response.put("status", "success");
                response.put("username", loggedInAccount.getUsername());
                return om.writeValueAsString(response);
            } else {
                session.setAttribute("Username", null);
                response.put("status", "failed");
                response.put("error", "Invalid username or password");
                return om.writeValueAsString(response);
            }
        } else {
            response.put("status", "failed");
            response.put("error", "Already logged in");
            return om.writeValueAsString(response);
        }
    }

    @PostMapping(value = "/signout", produces = "application/json")
    public String signOut(HttpSession session) throws JsonProcessingException {
        session.setAttribute("username", null);
        ObjectNode response = om.createObjectNode();
        response.put("status", "loggedout");
        return om.writeValueAsString(response);
    }

    // Check current login status
    @GetMapping(value = "/loginstatus", produces = "application/json")
    public String checkLogin(HttpSession session) throws IOException {
        String loggedInAs = (String) session.getAttribute("username");
        ObjectNode response = om.createObjectNode();
        if (loggedInAs != null) {
            response.put("status", "loggedin");
            response.put("username", loggedInAs);
            return om.writeValueAsString(response);
        } else {
            response.put("status", "notloggedin");
            return om.writeValueAsString(response);
        }
    }

    @PostMapping(value = "/blockuser", produces = "application/json")
    public @ResponseBody
    String blockUser(@RequestParam String account, HttpSession session) throws NoSuchAlgorithmException, JsonProcessingException {
        String currentUser = (String) session.getAttribute("username");
        Account currentAccount = db.getAccount(currentUser);
        Account targetAccount = db.getAccount(account);
        if(targetAccount == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "target account not found");
        if(currentAccount == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account error");
        db.addToBlockedList(currentAccount._id, targetAccount._id);
        ObjectNode response = om.createObjectNode();
        response.put("status", "User has been blocked.");
        return om.writeValueAsString(response);
    }

    @PostMapping(value = "/toggleblock", produces = "application/json")
    public @ResponseBody
    String toggleBlock(@RequestParam String targetaccount, HttpSession session) throws NoSuchAlgorithmException, JsonProcessingException {
        String currentUser = (String) session.getAttribute("username");
        ObjectNode response = om.createObjectNode();
        Account currentAccount = db.getAccount(currentUser);
        Account targetAccount = db.getAccount(targetaccount);
        if(targetAccount == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "target account not found");
        if(currentAccount == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account error");
        if(currentAccount.blockedUsers.contains(targetAccount._id)) {
            db.removeFromBlockedList(currentAccount._id, targetAccount._id);
            response.put("status", targetAccount.getUsername() + " has been unblocked.");
        }
        else {
            db.addToBlockedList(currentAccount._id, targetAccount._id);
            response.put("status", targetAccount.getUsername() + " has been blocked.");
        }
        return om.writeValueAsString(response);
    }




    @PostMapping(value = "/setprivate", produces = "application/json")
    public @ResponseBody
    String setPrivate(@RequestParam boolean privacy, HttpSession session) throws NoSuchAlgorithmException, JsonProcessingException {
        String currentUser = (String) session.getAttribute("username");
        Account currentAccount = db.getAccount(currentUser);
        if(currentAccount == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account error");
        db.changePrivacy(currentAccount._id, privacy);
        ObjectNode response = om.createObjectNode();
        response.put("status", "User privacy is set to " + privacy);
        return om.writeValueAsString(response);
    }

    @PostMapping(value = "/toggleprivacy", produces = "application/json")
    public @ResponseBody
    String togglePrivacy(HttpSession session) throws JsonProcessingException {
        String currentUser = (String) session.getAttribute("username");
        Account currentAccount = db.getAccount(currentUser);
        if(currentAccount == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account error");
        if(currentAccount.isPrivate()) {
            db.changePrivacy(currentAccount._id, false);
            ObjectNode response = om.createObjectNode();
            response.put("status", "User privacy is set to false");
            return om.writeValueAsString(response);
        } else {
            db.changePrivacy(currentAccount._id, true);
            ObjectNode response = om.createObjectNode();
            response.put("status", "User privacy is set to true");
            return om.writeValueAsString(response);
        }
    }

    @GetMapping(value = "/isprivate", produces = "application/json")
    public @ResponseBody
    String isPrivate(HttpSession session) throws JsonProcessingException {
        String currentUser = (String) session.getAttribute("username");
        Account currentAccount = db.getAccount(currentUser);
        if(currentAccount == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account error");
        if(currentAccount.isPrivate()) {
            ObjectNode response = om.createObjectNode();
            response.put("isprivate", "true");
            return om.writeValueAsString(response);
        } else {
            ObjectNode response = om.createObjectNode();
            response.put("status", "false");
            return om.writeValueAsString(response);
        }
    }


    @PostMapping(value = "/unblockuser", produces = "application/json")
    public @ResponseBody
    String unblockUser(@RequestParam String account, HttpSession session) throws NoSuchAlgorithmException, JsonProcessingException {
        String currentUser = (String) session.getAttribute("username");
        Account currentAccount = db.getAccount(currentUser);
        Account targetAccount = db.getAccount(account);
        if(targetAccount == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "target account not found");
        if(currentAccount == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account error");
        db.removeFromBlockedList(currentAccount._id, targetAccount._id);
        ObjectNode response = om.createObjectNode();
        response.put("status", "User has been unblocked.");
        return om.writeValueAsString(response);
    }
    
//    @PostMapping(value = "/unsetPrivate/{account:.+}", produces = "application/json")
//    public @ResponseBody
//    String unsetPrivate(@PathVariable String account, HttpSession session) throws NoSuchAlgorithmException, JsonProcessingException {
//        String currentUser = (String) session.getAttribute("username");
//        Account currentAccount = db.getAccount(currentUser);
//        String currentUserId = currentAccount.get_id();
//        db.changePrivacy(currentUserId, false);
//        ObjectNode response = om.createObjectNode();
//        response.put("status", "User has set to public.");
//        return om.writeValueAsString(response);
//    }

    @PostMapping(value = "/changepassword", produces = "application/json")
    public String changePassword(@RequestParam String oldpassword,
                                 @RequestParam String newpassword,
                                 HttpSession session) throws JsonProcessingException {
        String username = (String) session.getAttribute("username");
        String userid = (String) session.getAttribute("userid");
        ObjectNode response = om.createObjectNode();
        if (username == null) {
            response.put("status", "failed");
            response.put("error", "Not logged in");
            return om.writeValueAsString(response);
        } else {
            Account a = db.getAccount(username);
            if(a == null)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account");

            if(a.getPassword().equals(oldpassword)) {
                db.changePassword(a._id, newpassword);
                response.put("status", "success");
                return om.writeValueAsString(response);
            } else {
                response.put("status", "failed");
                response.put("error", "Old password incorrect");
                return om.writeValueAsString(response);
            }
        }
    }

    @PostMapping(value = "/changeemail", produces = "application/json")
    public String changeEmail(@RequestParam String newemail,
                                 HttpSession session) throws JsonProcessingException {
        String username = (String) session.getAttribute("username");
        String userid = (String) session.getAttribute("userid");
        ObjectNode response = om.createObjectNode();
        if (username == null) {
            response.put("status", "failed");
            response.put("error", "Not logged in");
            return om.writeValueAsString(response);
        } else {
            Account a = db.getAccount(username);
            if(a == null)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account");
            db.changeEmail(a._id, newemail);
            response.put("status", "success");
            return om.writeValueAsString(response);
        }
    }

    @PostMapping(value = "/changebio", produces = "application/json")
    public String changeBio(@RequestParam String bio,
                              HttpSession session) throws JsonProcessingException {
        String username = (String) session.getAttribute("username");
        String userid = (String) session.getAttribute("userid");
        ObjectNode response = om.createObjectNode();
        if (username == null) {
            response.put("status", "failed");
            response.put("error", "Not logged in");
            return om.writeValueAsString(response);
        } else {
            Account a = db.getAccount(username);
            if(a == null)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account");
            db.changeBio(a._id, bio);
            response.put("status", "success");
            return om.writeValueAsString(response);
        }
    }

    @PutMapping(value = "/updateprofile", produces = "application/json")
    public String updateProfile(@RequestParam(required = false) String firstname,
                                @RequestParam(required = false) String lastname,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String bio,
                                @RequestBody(required = false) MultipartFile image,
                                HttpSession session) throws IOException {
        String username = (String) session.getAttribute("username");
        String userid = (String) session.getAttribute("userid");
        ObjectNode response = om.createObjectNode();
        if (username == null) {
            response.put("status", "failed");
            response.put("error", "Not logged in");
            return om.writeValueAsString(response);
        } else {
            Account a = db.getAccount(username);
            if(a == null) {
                response.put("status", "failed");
                response.put("error", "could not find accounts");
                return om.writeValueAsString(response);
            }

            if (firstname != null) {
                db.changeFirstname(a._id, firstname);
                response.put("firstname", "updated");
            }
            if (lastname != null) {
                db.changeLastname(a._id, lastname);
                response.put("lastname", "updated");
            }
            if (email != null) {
                db.changeEmail(a._id, email);
                response.put("email", "updated");
            }
            if (bio != null) {
                db.changeBio(a._id, bio);
                response.put("bio", "updated");
            }
            if(image != null) {
                Map uploadResult = cloudinary.uploader()
                        .upload(image.getBytes(), ObjectUtils.emptyMap());
                String url = (String) uploadResult.get("url");
                db.setProfilePicture(a._id, url);
                response.put("image", "updated");
            }
            return om.writeValueAsString(response);
        }
    }

    @PostMapping(value = "/followtoggle", produces = "application/json")
    public String followToggle(@RequestParam String targetaccount, HttpSession session) throws IOException {
        String username = (String) session.getAttribute("username");
        String userid = (String) session.getAttribute("userid");
        ObjectNode response = om.createObjectNode();
        if (username == null) {
            response.put("status", "failed");
            response.put("error", "Not logged in");
            return om.writeValueAsString(response);
        } else {
            Account a = db.getAccount(username);
            Account target = db.getAccount(targetaccount);
            if(a == null || target == null) {
                response.put("status", "failed");
                response.put("error", "could not find accounts");
                return om.writeValueAsString(response);
            }
            if(!a.followedUsers.contains(target._id)) {
                if(db.followUser(target._id, a._id)) {
                    response.put("status", "success");
                    response.put("error", "Now following user " + targetaccount);
                    return om.writeValueAsString(response);
                } else {
                    response.put("status", "failed");
                    response.put("error", "user is private");
                    return om.writeValueAsString(response);
                }
            } else {
                db.unfollowUser(target._id, a._id);
                response.put("status", "success");
                response.put("error", "Unfollowed user " + targetaccount);
                return om.writeValueAsString(response);
            }
        }
    }

    @GetMapping(value = "/isfollowing", produces = "application/json")
    public String isFollowing(@RequestParam String targetaccount, HttpSession session ) throws JsonProcessingException {
        String username = (String) session.getAttribute("username");
        String userid = (String) session.getAttribute("userid");
        ObjectNode response = om.createObjectNode();
        if (username == null) {
            response.put("status", "failed");
            response.put("error", "Not logged in");
            return om.writeValueAsString(response);
        } else {
            Account user = db.getAccount(username);
            Account target = db.getAccount(targetaccount);
            if(user == null || target == null) {
                response.put("status", "failed");
                response.put("error", "User not found");
                return om.writeValueAsString(response);
            }
            if (user.followedUsers.contains(target._id)) {
                response.put("isfollowing", true);
                response.put("account", targetaccount);
                return om.writeValueAsString(response);
            } else {
                response.put("isfollowing", false);
                response.put("account", targetaccount);
                return om.writeValueAsString(response);
            }
        }
    }
    @PostMapping(value = "/follow", produces = "application/json")
    public String followuser(@RequestParam String targetaccount,
                             HttpSession session) throws JsonProcessingException {
        String username = (String) session.getAttribute("username");
        String userid = (String) session.getAttribute("userid");
        ObjectNode response = om.createObjectNode();
        if (username == null) {
            response.put("status", "failed");
            response.put("error", "Not logged in");
            return om.writeValueAsString(response);
        } else {
            Account followTarget = db.getAccount(targetaccount);
            Account user = db.getAccount(new ObjectId(userid));
            if (followTarget == null) {
                response.put("status", "failed");
                response.put("error", "User not found");
                return om.writeValueAsString(response);
            } else {
                db.followUser(followTarget._id, user._id);
                response.put("status", "success");
                response.put("error", "Now following user " + targetaccount);
                return om.writeValueAsString(response);
            }
        }
    }

    @PostMapping(value = "/unfollow", produces = "application/json")
    public String unfollowuser(@RequestParam String targetaccount,
                               HttpSession session) throws JsonProcessingException {
        String username = (String) session.getAttribute("username");
        String userid = (String) session.getAttribute("userid");
        ObjectNode response = om.createObjectNode();
        if (username == null) {
            response.put("status", "failed");
            response.put("error", "Not logged in");
            return om.writeValueAsString(response);
        } else {
            Account followTarget = db.getAccount(targetaccount);
            Account user = db.getAccount(new ObjectId(userid));
            if (followTarget == null) {
                response.put("status", "failed");
                response.put("error", "User not found");
                return om.writeValueAsString(response);
            } else {
                db.unfollowUser(followTarget._id, user._id);
                response.put("status", "success");
                response.put("error", "Unfollowed user " + targetaccount);
                return om.writeValueAsString(response);
            }
        }
    }

    @GetMapping(value = "/account/{account:.+}", produces = "application/json")
    public @ResponseBody
    String getAccount(@PathVariable String account) throws IOException {
        ObjectId accId;
        try {
            accId = new ObjectId(account);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid user account");
        }
        Account requestedAccount = db.getAccount(accId);
        if (requestedAccount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid user account");
        }
        ObjectNode response = om.createObjectNode();
        response.put("_id", requestedAccount.get_id());
        response.put("username", requestedAccount.getUsername());
        response.put("firstName", requestedAccount.getFirstName());
        response.put("lastName", requestedAccount.getLastName());

        return om.writeValueAsString(response);
    }

    @GetMapping(value = "/getuser", produces = "application/json")
    public @ResponseBody
    String getUser(@RequestParam String userid, HttpSession session) throws IOException {
        String loggedInAs = (String) session.getAttribute("username");
        Account currentUser = db.getAccount(loggedInAs);

        if(currentUser == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid logged in account");
        if(userid == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no userid received");

        Account account = db.getAccount(userid);
        if(account == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no account found with that id");
        }

        ObjectNode response = om.createObjectNode();
        response.put("_id", account.get_id());
        response.put("username", account.getUsername());
        response.put("firstName", account.getFirstName());
        response.put("lastName", account.getLastName());
        if(account.blockedUsers.contains(currentUser._id)){
            response.put("status", "blockedbythem");
        } else if(currentUser.blockedUsers.contains(account._id)){
            response.put("status", "blockedbyyou");
        } else if(!account.followedUsers.contains(currentUser._id) && account.isPrivate) {
            response.put("status", "private");
        } else {
            response.put("status", "public");
        }
        response.put("bio", account.getBio());
        response.put("email", account.getEmail());
        response.put("profilepicture", account.getProfilepicture());
        response.put("followercount", account.getFollowedBy().size());
        response.put("followingcount", account.getFollowedUsers().size());
        ArrayList<ObjectId> postids = account.getPosts();
        Iterator<ObjectId> iterator = postids.iterator();
        ArrayList<Post> posts = new ArrayList<>();
        while(iterator.hasNext()) {
            Post p = db.getPost(iterator.next());
            posts.add(p);
        }
        response.putPOJO("posts", posts);
        return om.writeValueAsString(response);
    }

    @PostMapping(value = "/makepost", produces = "application/json")
    public @ResponseBody
    String makePost(@RequestParam String[] images,
                    @RequestParam String description,
                    HttpSession session) throws JsonProcessingException {
        String loggedInAs = (String) session.getAttribute("username");
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        ArrayList<String> imageList = new ArrayList<>();
        Account a = db.getAccount(new ObjectId(loggedInAs));
        try {
            for (int i = 0; i < images.length; i++) {
                imageList.add(images[i]);
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid image id");
        }
        Post p = new Post(imageList, a._id, a.getUsername(), description);
        db.insertPost(p);
        ObjectNode response = om.createObjectNode();
        response.put("_id", p.get_id().toHexString());
        return om.writeValueAsString(response);
    }

    @GetMapping(value = "/accountposts", produces = "application/json")
    public @ResponseBody
    String getAccountPosts(HttpSession session) throws JsonProcessingException {
        String username = (String) session.getAttribute("username");
        String userid = (String) session.getAttribute("userid");
        if (username == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        Account account = db.getAccount(username);
        if (account == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account");

        ObjectNode response = om.createObjectNode();
        response.put("_id", account.get_id());
        response.put("username", account.getUsername());
        response.put("firstName", account.getFirstName());
        response.put("lastName", account.getLastName());
        response.put("bio", account.getBio());
        response.put("email", account.getEmail());
        response.put("profilepicture", account.getProfilepicture());
        response.put("followercount", account.getFollowedBy().size());
        response.put("followingcount", account.getFollowedUsers().size());

        ArrayList<Post> allPosts = new ArrayList<>();
        ArrayList<ObjectId> myPosts = account.getPosts();
        Iterator<ObjectId> myPostItr = myPosts.iterator();
        while(myPostItr.hasNext()){
            ObjectId currentPost = myPostItr.next();
            Post p = db.getPost(currentPost);
            if(p == null) continue;
            allPosts.add(db.getPost(currentPost));
        }
        Collections.sort(allPosts, Collections.reverseOrder());
        response.putPOJO("posts", allPosts);
        return om.writeValueAsString(response);
    }

    @GetMapping(value = "/feed", produces = "application/json")
    public @ResponseBody ArrayList<Post> feed(@RequestParam(required = false) String sort,
                                              HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        Account account = db.getAccount(username);
        if (account == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account");

        ArrayList<ObjectId> followedUsers = account.getFollowedUsers();
        Iterator<ObjectId> followedUsersItr = followedUsers.iterator();
        ArrayList<Post> allPosts = new ArrayList<>();

        ArrayList<ObjectId> myPosts = account.getPosts();
        Iterator<ObjectId> myPostItr = myPosts.iterator();
        while(myPostItr.hasNext()){
            ObjectId currentPost = myPostItr.next();
            Post p = db.getPost(currentPost);
            if(p == null) continue;
            allPosts.add(db.getPost(currentPost));
        }

        while(followedUsersItr.hasNext()) {
            ObjectId currentUserId = followedUsersItr.next();
            Account currentUser = db.getAccount(currentUserId);
            ArrayList<ObjectId> posts = currentUser.getPosts();
            Iterator<ObjectId> postItr = posts.iterator();
            while(postItr.hasNext()){
                ObjectId currentPost = postItr.next();
                Post p = db.getPost(currentPost);
                if(p == null) continue;
                allPosts.add(db.getPost(currentPost));
            }
        }
        if(sort != null) {
            if (sort.equals("likesdescending")) {
                allPosts.sort(new Comparator<Post>() {
                    @Override
                    public int compare(Post post, Post t1) {
                        if (post.getLikes() > t1.getLikes()) {
                            return -1;
                        } else {
                            return 1;
                        }
                    }
                });
            } else if (sort.equals("likesascending")) {
                allPosts.sort(new Comparator<Post>() {
                    @Override
                    public int compare(Post post, Post t1) {
                        if (post.getLikes() > t1.getLikes()) {
                            return -1;
                        } else {
                            return 1;
                        }
                    }
                });
            } else if (sort.equals("dateascending")) {
                Collections.sort(allPosts, Collections.reverseOrder());
            } else if (sort.equals("datedescending")) {
                Collections.sort(allPosts);
            }
        } else {
            Collections.sort(allPosts, Collections.reverseOrder());
        }
        return allPosts;
    }

    @PostMapping(value = "/createalbum", produces = "application/json")
    public @ResponseBody
    String createAlbum(@RequestParam String name,
                    HttpSession session) throws JsonProcessingException {
        String loggedInAs = (String) session.getAttribute("username");
        ObjectNode response = om.createObjectNode();
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        ArrayList<String> imageList = new ArrayList<>();
        Account a = db.getAccount(loggedInAs);

        if(a != null) {
            Album newAlbum = db.createAlbum(a._id, name);
            response.put("_id", newAlbum._id.toHexString());
            return om.writeValueAsString(response);
        } else {
            response.put("error", "invalid account");
            return om.writeValueAsString(response);
        }
    }

    @GetMapping(value = "/getalbum", produces = "application/json")
    public @ResponseBody
    Album getAlbum(@RequestParam String name, HttpSession session) throws IOException {
        String loggedInAs = (String) session.getAttribute("username");
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Account account = db.getAccount(loggedInAs);
        for (ObjectId album : account.getAlbums()) {
            Album currentAlbum = db.getAlbum(album);
            if(currentAlbum == null) continue;
            if(currentAlbum.getName().equals(name)) {
                return currentAlbum;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "could not find album");
    }

    @GetMapping(value = "/getallalbums", produces = "application/json")
    public @ResponseBody
    String getAllAlbums(HttpSession session) throws IOException {
        String loggedInAs = (String) session.getAttribute("username");
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Account account = db.getAccount(loggedInAs);
        ObjectNode response = om.createObjectNode();
        int count = 0;
        for (ObjectId album : account.getAlbums()) {
            ObjectNode node = response.putObject(String.valueOf(count));
            Album currentAlbum = db.getAlbum(album);
            if(currentAlbum != null) {
                node.put("name", currentAlbum.getName());
                node.put("id", currentAlbum.get_id().toHexString());
                count++;
            }
        }
        return om.writeValueAsString(response);
    }

    @GetMapping(value = "/getfollowers", produces = "application/json")
    public @ResponseBody String getFollowers(HttpSession session) throws JsonProcessingException {
        String username = (String) session.getAttribute("username");
        if(username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }

        Account a = db.getAccount(username);
        ObjectNode response = om.createObjectNode();
        int count = 0;
        for (ObjectId followedUser : a.getFollowedUsers()) {
            Account follower = db.getAccount(followedUser);
            if(follower != null) {
                response.put(String.valueOf(count), follower.getUsername());
                count++;
            }
        }
        return om.writeValueAsString(response);
    }

    @PostMapping(value = "/addusertoalbum", produces = "application/json")
    public @ResponseBody
    String addUserToAlbum(@RequestParam String username,
                          @RequestParam String album,
                          HttpSession session) throws JsonProcessingException {
        String loggedInAs = (String) session.getAttribute("username");
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Account account = db.getAccount(loggedInAs);
        Account target = db.getAccount(username);
        ObjectNode response = om.createObjectNode();
        for (ObjectId albumId : account.getAlbums()) {
            Album currentAlbum = db.getAlbum(albumId);
            if(currentAlbum == null) continue;
            if(currentAlbum.getName().equals(album)) {
                if(currentAlbum.getGroup().contains(target._id)) {
                    response.put("status", "failed");
                    response.put("error", "user already in album group");
                    return om.writeValueAsString(response);
                }
                db.addUserToAlbum(currentAlbum.get_id(), username);
                response.put("status", "success");
                response.put("error", "user successfully added to album");
                return om.writeValueAsString(response);

            }
        }
        response.put("status", "failed");
        return om.writeValueAsString(response);
    }

    @PostMapping(value = "/addimagetoalbum", produces = "application/json")
    public @ResponseBody
    String addImageToAlbum(@RequestParam MultipartFile image,
                           @RequestParam String album,
                           HttpSession session) throws IOException {
        String loggedInAs = (String) session.getAttribute("username");
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Account account = db.getAccount(loggedInAs);
        ObjectNode response = om.createObjectNode();
        Iterator<ObjectId> itr = account.getAlbums().iterator();
        while (itr.hasNext()) {
            ObjectId albumId = itr.next();
            Album currentAlbum = db.getAlbum(albumId);
            if(currentAlbum == null) continue;
            if(currentAlbum.getName().equals(album)) {
                Map uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.emptyMap());
                db.addImageToAlbum(currentAlbum.get_id(), (String)uploadResult.get("url"));
                response.put("status", "success");
                response.put("url", (String)uploadResult.get("url"));
                return om.writeValueAsString(response);

            }
        }
        response.put("status", "failed");
        return om.writeValueAsString(response);
    }

    @DeleteMapping(value = "/removeuserfromalbum", produces = "application/json")
    public @ResponseBody
    String removeUserFromAlbum(@RequestParam String username,
                               @RequestParam String album,
                               HttpSession session) throws JsonProcessingException {
        String loggedInAs = (String) session.getAttribute("username");
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Account account = db.getAccount(loggedInAs);
        Account target = db.getAccount(username);
        ObjectNode response = om.createObjectNode();
        for (ObjectId albumId : account.getAlbums()) {
            Album currentAlbum = db.getAlbum(albumId);
            if(currentAlbum == null) continue;
            if(currentAlbum.getName().equals(album)) {
                if(currentAlbum.getGroup().contains(target._id)) {
                    db.removeUserFromAlbum(username, currentAlbum.get_id());
                    response.put("status", "success");
                    response.put("error", "user removed from album");
                    return om.writeValueAsString(response);
                } else {
                    response.put("status", "failed");
                    response.put("error", "user not in album group");
                    return om.writeValueAsString(response);
                }
            }
        }
        response.put("status", "failed");
        return om.writeValueAsString(response);
    }

    // returns the 9 highest liked public posts
    @GetMapping(value = "/popularposts", produces = "application/json")
    public @ResponseBody ArrayList<Post> popularPosts() {
        return db.getPopularPosts();
    }

    @GetMapping(value = "/searchusername", produces = "application/json")
    public @ResponseBody ArrayList<String> searchUser(@RequestParam String query, HttpSession session) throws NoSuchAlgorithmException, IOException {
        return db.searchUsername(query);
    }

    @GetMapping(value = "/searchimagedesc", produces = "application/json")
    public @ResponseBody ArrayList<Post> searchImageDesc(@RequestParam String query, HttpSession session) throws NoSuchAlgorithmException, IOException {
        String loggedInAs = (String) session.getAttribute("username");
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        return db.getPost(query, loggedInAs);
    }

    @GetMapping(value = "/getimagesforsale", produces = "application/json")
    public @ResponseBody String getimagesforsale(HttpSession session) throws IOException {
        String loggedInAs = (String) session.getAttribute("username");
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Account a = db.getAccount(loggedInAs);
        ObjectNode response = om.createObjectNode();
        ArrayList<ObjectId> postIdList = a.getPosts();
        int count = 0;
        for (ObjectId postId : postIdList) {
            Post post = db.getPost(postId);
            ObjectNode node = response.putObject(String.valueOf(count));
            count++;
            node.put("id", post.get_id().toHexString());
            node.put("url", post.getImageId().get(0));
            node.put("description", post.getDescription());
            node.put("price", post.getPrice());
        }
        return om.writeValueAsString(response);
    }

    @PostMapping(value = "/setprice", produces = "application/json")
    public @ResponseBody String setprice(@RequestParam String id,
                                         @RequestParam String price,
                                         HttpSession session) throws IOException {
        String loggedInAs = (String) session.getAttribute("username");
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Account a = db.getAccount(loggedInAs);
        Post p = db.getPost(new ObjectId(id));
        ObjectNode response = om.createObjectNode();
        response.put("status", "success");
        response.put("price", "success");
        return om.writeValueAsString(response);
    }

    @GetMapping(value = "/searchtag", produces = "application/json")
    public @ResponseBody ArrayList<Post> searchByTag(@RequestParam String query, HttpSession session) throws NoSuchAlgorithmException, IOException {
        String loggedInAs = (String) session.getAttribute("username");
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        return db.searchTag(query, loggedInAs);
    }

    @PostMapping(value = "/searchlocation", produces = "application/json")
    public @ResponseBody ArrayList<Post> searchLocation(@RequestParam String longitude,
                                                        @RequestParam String latitude,
                                                        HttpSession session) {
        String loggedInAs = (String) session.getAttribute("username");
        if(loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        Account a = db.getAccount(loggedInAs);
        if(a == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account");
        }
        ArrayList<Post> posts = db.searchLocation(loggedInAs,
                Double.parseDouble(longitude),
                Double.parseDouble(latitude));
        return posts;
    }

    @GetMapping(value = "/search", produces = "application/json")
    public @ResponseBody String search(@RequestParam String query, HttpSession session) throws IOException {
        String loggedInAs = (String) session.getAttribute("username");
        if (loggedInAs == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not logged in");
        }
        if(query == null || query.length() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "search is empty");
        }
        ObjectNode response = om.createObjectNode();
        ArrayList<String> usernames = db.searchUsername(query);
        Iterator<String> itr = usernames.iterator();
        ArrayNode usernameArray = response.putArray("username");
        while(itr.hasNext()){
            ObjectNode node = usernameArray.addObject();
            node.put("name", itr.next());
            node.put("profilepicture", itr.next());
        }
        ArrayList<Post> posts = db.getPost(query, loggedInAs);
        response.putPOJO("description", posts);
        ArrayList<DatabaseController.TagResult> tagPosts = db.getTags(query);
        ArrayNode tagArray = response.putArray("tag");
        Iterator<DatabaseController.TagResult> itr2 = tagPosts.iterator();
        while(itr2.hasNext()) {
            DatabaseController.TagResult next = itr2.next();
            ObjectNode node = tagArray.addObject();
            node.put("tag", next.tag);
            node.put("id", next._id);
            node.put("username", next.username);
        }
        //response.putPOJO("tag", tagPosts);
        return om.writeValueAsString(response);
    }


}