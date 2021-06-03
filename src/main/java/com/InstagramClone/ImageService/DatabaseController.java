package com.InstagramClone.ImageService;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.include;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.regex.Pattern;

import com.InstagramClone.model.*;
import com.mongodb.BasicDBObject;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.model.geojson.Point;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.ConnectionString;


public class DatabaseController {
    private static DatabaseController single_instance = null; 
	
	private MongoClient mongoClient;
	private MongoDatabase database;
//	private MongoCollection<Image> imageDb;
	private MongoCollection<Post> postDb;
	private MongoCollection<Account> accountDb;
	private MongoCollection<Album> albumDb;

	private DatabaseController () {
		Properties prop = new Properties();
		String propFileName = "db.properties";
		InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
		
		try {
			prop.load(inputStream);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String username = prop.getProperty("username");
		String password = prop.getProperty("password");
		
	    mongoClient = MongoClients.create(new ConnectionString
	    		("mongodb+srv://"+username+":"+password+"@cluster0-lhmsj.mongodb.net/test?retryWrites=true&w=majority"));
		CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(),
				fromProviders(PojoCodecProvider.builder().automatic(true).build()));
		database = mongoClient.getDatabase("db");
	    database = database.withCodecRegistry(pojoCodecRegistry);
//	    imageDb = database.getCollection("Images", Image.class);
	    postDb = database.getCollection("Posts", Post.class);
	    postDb.createIndex(Indexes.text("description"));
	    postDb.createIndex(Indexes.geo2dsphere("gps"));
	    accountDb = database.getCollection("Accounts", Account.class);
		albumDb = database.getCollection("Albums", Album.class);
	}

//	// Return image object given an objectid as a string
//	public Image getImage(String id) {
//		return imageDb.find(eq("_id", new ObjectId(id))).first();
//	}
	
	// Create an account from a given account object
	public String createAccount(Account account) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(account.getPassword().getBytes());
		byte[] digest = md.digest();
		StringBuffer sb = new StringBuffer();
		for (byte b : digest) {
			sb.append(String.format("%02x", b & 0xff));
		}
		account.setPassword(sb.toString());
		accountDb.insertOne(account);
		return account.get_id();
	}
	
	//Create a privacy flag table with same id as the account._id and a flag
//	public void createPrivacy(Privacy privacy) throws NoSuchAlgorithmException {
//		privacyDb.insertOne(privacy);
//	}
//
//	public void createBlockList(BlockedUser blockedUsers) throws NoSuchAlgorithmException {
//		blockedUserDb.insertOne(blockedUsers);
//	}
	
	public Account checkAccount(String username, String password) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(password.getBytes());
		byte[] digest = md.digest();
		StringBuffer sb = new StringBuffer();
		for (byte b : digest) {
			sb.append(String.format("%02x", b & 0xff));
		}
		Account login = accountDb.find(and(eq("username", username), eq("password", sb.toString()))).first();
		if(login != null) {
			return login;
		} else {
			return null;
		}
	}

	public boolean followUser(ObjectId targetAccount, ObjectId currentAccount) {
		Account target = getAccount(targetAccount);
		if(target.isPrivate && !target.followedUsers.contains(currentAccount)){
			return false;
		}
		accountDb.updateOne(eq("_id", currentAccount), Updates.addToSet("followedUsers", targetAccount));
		accountDb.updateOne(eq("_id", targetAccount), Updates.addToSet("followedBy", currentAccount));
		return true;
	}

	public void changePassword(ObjectId targetAccount, String newPassword) {
		accountDb.updateOne(eq("_id", targetAccount), Updates.set("password", newPassword));
	}

	public void changeEmail(ObjectId targetAccount, String newEmail) {
		accountDb.updateOne(eq("_id", targetAccount), Updates.set("email", newEmail));
	}

	public String unfollowUser(ObjectId targetAccount, ObjectId currentAccount) {
		accountDb.updateOne(eq("_id", currentAccount), Updates.pull("followedUsers", targetAccount));
		accountDb.updateOne(eq("_id", targetAccount), Updates.pull("followedBy", currentAccount));
		return "success";
	}
	// Return account object given an objectid
	public Account getAccount(ObjectId id) {
		return accountDb.find(eq("_id", id)).first();
	}

	public ArrayList<String> searchUsername(String search) {
		Pattern regex = Pattern.compile(search, Pattern.CASE_INSENSITIVE);
		Bson filter = Filters.eq("username", regex);
		MongoCursor<Account> iterator = accountDb.find(filter).iterator();
		ArrayList<String> list = new ArrayList<>();
		while(iterator.hasNext()) {
			Account a = iterator.next();
			list.add(a.getUsername());
			list.add(a.getProfilepicture());
		}
		return list;
	}

	// Return account object given an username as a string
	public Account getAccount(String username) {
		return accountDb.find(eq("username", username)).first();
	}

	public Post getPost(ObjectId id){
		return postDb.find(eq("_id", id)).first();
	}

	public ArrayList<Post> getPost(String username, String description){
		FindIterable<Post> posts  = postDb.find(Filters.text(description));
		MongoCursor<Post> iterator = posts.iterator();
		ArrayList<Post> response = new ArrayList<>();
		while(iterator.hasNext()) {
			response.add(iterator.next());
		}
		return filterPosts(response, username);
	}

	public ArrayList<Post> searchTag(String username, String tag){
		MongoCursor<Post> iterator = postDb.find(eq("tags", tag)).iterator();
		ArrayList<Post> list = new ArrayList<>();
		while(iterator.hasNext()) {
			Post a = iterator.next();
			list.add(a);
		}
		return filterPosts(list, username);
	}

	public ArrayList<Post> searchLocation(String username, double longitude, double latitude) {
		BasicDBObject query = new BasicDBObject("gps",
				new BasicDBObject ("$near", new BasicDBObject("type","Point")
						.append("coordinates",new double[] {longitude,latitude}))
						.append("maxDistance", 250));
		FindIterable<Post> results = postDb.find(
				Filters.nearSphere("gps", longitude, latitude, 100000.0d, 0.0d))
				.limit(10);
		MongoCursor<Post> iterator = results.iterator();
		ArrayList<Post> r = new ArrayList<>();
		while(iterator.hasNext()) {
			r.add(iterator.next());
		}
		return filterPosts(r, username);
	}

	public ArrayList<Post> filterPosts(ArrayList<Post> posts, String username) {
		Account a = getAccount(username);
		ArrayList<Post> result = new ArrayList<>();
		for (Post p : posts) {
			Account currentAccount = getAccount(p.getUsername());
			if(username.equals(p.getUsername())) {
				result.add(p);
			} else if(!currentAccount.isPrivate()) {
				result.add(p);
			} else if(currentAccount.isPrivate() && currentAccount.getFollowedUsers()
					.contains(a._id)) {
				result.add(p);
			}
		}
		return result;
	}

	public ArrayList<TagResult> getTags(String tag){
		Pattern regex = Pattern.compile(tag, Pattern.CASE_INSENSITIVE);
		Bson filter = Filters.eq("tags", regex);
		MongoCursor<Post> iterator = postDb.find(filter).iterator();
		ArrayList<TagResult> tagList = new ArrayList<>();
		while(iterator.hasNext()) {
			Post a = iterator.next();
			for (String s : a.getTags()) {
				if(regex.matcher(s).find()) {
					tagList.add(new TagResult(s, a._id.toHexString(), a.getUsername()));
				}
			}
		}
		tagList.sort(new MyComparator(tag));
		return tagList;
	}

	public class TagResult {
		public String tag;
		public String _id;
		public String username;
		public TagResult (String tag, String _id, String username) {
			this.tag = tag;
			this._id = _id;
			this.username = username;
		}
	}

	public boolean getPrivacy(String username) {
		Account account = getAccount(username);
		return account.isPrivate;
	}
	
	public ArrayList<ObjectId> getBlockList(String username) {
		Account account = getAccount(username);
		return account.blockedUsers;
	}

	public void setProfilePicture(ObjectId targetAccount, String url) {
		accountDb.updateOne(eq("_id", targetAccount), Updates.set("profilepicture", url));
	}

	public void changeBio(ObjectId targetAccount, String bio) {
		accountDb.updateOne(eq("_id", targetAccount), Updates.set("bio", bio));
	}

	public void changeFirstname(ObjectId targetAccount, String firstname) {
		accountDb.updateOne(eq("_id", targetAccount), Updates.set("firstName", firstname));
	}

	public void changeLastname(ObjectId targetAccount, String lastname) {
		accountDb.updateOne(eq("_id", targetAccount), Updates.set("lastName", lastname));
	}

	// Makes a post on the database
	public void insertPost(Post post) {
		postDb.insertOne(post);
		accountDb.updateOne(eq("_id", new ObjectId(post.getAccount().toHexString())),
				Updates.addToSet("posts", post.get_id()));
	}

	public boolean likePost(Account account, Post post) {
		ArrayList<ObjectId> likedPosts = account.getLikedPosts();
		if(!likedPosts.contains(post._id)) {
			accountDb.updateOne(eq("_id", account._id), Updates.addToSet("likedPosts", post._id));
			postDb.updateOne(eq("_id", post._id), Updates.inc("likes", 1));
			return true;
		} else return false;
	}

	public boolean  unlikePost(Account account, Post post) {
		ArrayList<ObjectId> likedPosts = account.getLikedPosts();
		if(likedPosts.contains(post._id)) {
			accountDb.updateOne(eq("_id", account._id), Updates.pull("likedPosts", post._id));
			postDb.updateOne(eq("_id", post._id), Updates.inc("likes", -1));
			return true;
		} else return false;
	}

    public void writeComment(Account account, Post post, String comment) {
        postDb.updateOne(eq("_id", post._id),
				Updates.addToSet("comments",
						new Comment(account.getUsername(), account._id, comment)));
    }

	public void writeComment(Account account, Post post, String comment, String imageUrl) {
		postDb.updateOne(eq("_id", post._id),
				Updates.addToSet("comments",
						new Comment(account.getUsername(), account._id, comment, imageUrl)));
	}

    public ArrayList<Post> getPopularPosts() {
		FindIterable<Post> posts = postDb.find().sort(Sorts.descending("likes")).limit(9);
		MongoCursor<Post> iterator = posts.iterator();
		ArrayList<Post> response = new ArrayList<Post>();
		while(iterator.hasNext()) {
			response.add(iterator.next());
		}
		return response;
	}

	public Album createAlbum(ObjectId accId, String name) {
		Account account = getAccount(accId);
		Album album = new Album(account._id, account.getUsername(), name);
		accountDb.updateOne(eq("_id", account._id), Updates.addToSet("albums", album._id));
		albumDb.insertOne(album);
		return album;
	}

	public Album getAlbum(ObjectId id) {
		return albumDb.find(eq("_id", id)).first();
	}

	public void addUserToAlbum(ObjectId albumId, String username) {
		Album album = getAlbum(albumId);
		if(album == null) return;
		Account account = getAccount(username);
		accountDb.updateOne(eq("_id", account._id), Updates.addToSet("albums", album._id));
		albumDb.updateOne(eq("_id", albumId), Updates.addToSet("group", account._id));
	}

	public void addImageToAlbum(ObjectId albumId, String imageUrl) {
		Album album = getAlbum(albumId);
		if(album == null) return;
		albumDb.updateOne(eq("_id", albumId), Updates.addToSet("images", imageUrl));
	}

	public ArrayList<ObjectId> getAllAlbums(ObjectId accId) {
		Account account = getAccount(accId);
		ArrayList<ObjectId> response = account.getAlbums();
		return response;
	}

	public void removeUserFromAlbum(String username, ObjectId albumId) {
		Account account = getAccount(username);
		accountDb.updateOne(eq("_id", account._id), Updates.pull("albums", albumId));
		albumDb.updateOne(eq("_id", albumId), Updates.pull("group", account._id));
	}


    public static DatabaseController getInstance()
    { 
        if (single_instance == null) 
            single_instance = new DatabaseController(); 
  
        return single_instance; 
    }
    
    public void changePrivacy(ObjectId currentUserId, boolean isPrivate) {
    	accountDb.updateOne(eq("_id", currentUserId), Updates.set("isPrivate", isPrivate));
	}
  
    public void addToBlockedList(ObjectId currentUser, ObjectId targetUser) {
		Account account = accountDb.find(eq("_id", currentUser)).first();
		if(!account.blockedUsers.contains(targetUser)) {
			accountDb.updateOne(eq("_id", currentUser), Updates.addToSet("blockedUsers", targetUser));
		}
	}
    
    public void removeFromBlockedList(ObjectId currentUser, ObjectId targetUser) {
		accountDb.updateOne(eq("_id", currentUser), Updates.pull("blockedUsers", targetUser));
	}

	public ArrayList<Post> duplicateImageSearch(String phash, String username) {
        FindIterable<Post> postsResult = postDb.find(eq("phash", phash));
        ArrayList<Post> posts = new ArrayList<>();
        for (Post p : postsResult) {
            posts.add(p);
        }
        return filterPosts(posts, username);
    }

    public ArrayList<Post> imageSearch(String phash, String username) throws DecoderException {
        FindIterable<Post> postsResult = postDb.find(exists("phash"));
        ArrayList<Post> posts = new ArrayList<>();
        for (Post p : postsResult) {
            byte[] xor = xorHex(phash, p.getPhash());
            int score = 0;
            for (byte b : xor) {
                score += Integer.bitCount(b);
            }
            float phashScore = (float) (1.0 - (score / 64.0));
            if(phashScore >= 0.55) {
            	System.out.println("DING!");
                posts.add(p);
            }
        }
        return filterPosts(posts, username);
    }

    public byte[] xorHex(String a, String b) throws DecoderException {
        char[] chars = new char[a.length()];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = toHex(fromHex(a.charAt(i)) ^ fromHex(b.charAt(i)));
        }
        return Hex.decodeHex(chars);
    }

    private static int fromHex(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        throw new IllegalArgumentException();
    }

    private char toHex(int n) {
        if (n < 0 || n > 15) {
            throw new IllegalArgumentException();
        }
        return "0123456789ABCDEF".charAt(n);
    }

	class MyComparator implements Comparator<TagResult> {

		private final String keyWord;

		MyComparator(String keyWord) {
			this.keyWord = keyWord;
		}

		@Override
		public int compare(TagResult tagResult, TagResult t1) {
			if(tagResult.tag.startsWith(keyWord)) {
				return t1.tag.startsWith(keyWord)? tagResult.tag.compareTo(t1.tag): -1;
			} else {
				return t1.tag.startsWith(keyWord)? 1: tagResult.tag.compareTo(t1.tag);
			}
		}
	}

}
