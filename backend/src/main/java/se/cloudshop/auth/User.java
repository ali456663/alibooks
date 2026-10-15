package se.cloudshop.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "app_users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String email;
  private String password;

  @Column(name = "mfa_secret_enc", columnDefinition = "text")
  private String mfaSecretEnc;

  @Column(name = "mfa_pending_secret_enc", columnDefinition = "text")
  private String mfaPendingSecretEnc;

  @Column(name = "mfa_enabled", nullable = false, columnDefinition = "boolean default false")
  private boolean mfaEnabled;

  @Column(name = "mfa_last_counter", nullable = false)
  private long mfaLastCounter = -1;

  @Column(name = "mfa_recovery_hashes", columnDefinition = "text")
  private String mfaRecoveryHashes;

  @Column(name = "auth_version", nullable = false, columnDefinition = "bigint default 0")
  private long authVersion;

  public User() {
  }

  public User(String email, String password) {
    this.email = email;
    this.password = password;
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPassword() {
    return password;
  }

  public String getMfaSecretEnc() { return mfaSecretEnc; }
  public void setMfaSecretEnc(String value) { mfaSecretEnc = value; }
  public String getMfaPendingSecretEnc() { return mfaPendingSecretEnc; }
  public void setMfaPendingSecretEnc(String value) { mfaPendingSecretEnc = value; }
  public boolean isMfaEnabled() { return mfaEnabled; }
  public void setMfaEnabled(boolean value) { mfaEnabled = value; }
  public long getMfaLastCounter() { return mfaLastCounter; }
  public void setMfaLastCounter(long value) { mfaLastCounter = value; }
  public String getMfaRecoveryHashes() { return mfaRecoveryHashes; }
  public void setMfaRecoveryHashes(String value) { mfaRecoveryHashes = value; }
  public long getAuthVersion() { return authVersion; }
  public void rotateAuthVersion() { authVersion = Math.addExact(authVersion, 1); }
}
