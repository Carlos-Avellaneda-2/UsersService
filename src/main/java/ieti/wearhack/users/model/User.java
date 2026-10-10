package ieti.wearhack.users.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String name;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "google_id", unique = true)
	private String googleId;

	@Column(length = 2048)
	private String picture;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public User() {
	}

	public User(String name, String email, String googleId, String picture) {
		this.name = name;
		this.email = email;
		this.googleId = googleId;
		this.picture = picture;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getGoogleId() {
		return googleId;
	}

	public void setGoogleId(String googleId) {
		this.googleId = googleId;
	}

	public String getPicture() {
		return picture;
	}

	public void setPicture(String picture) {
		this.picture = picture;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
