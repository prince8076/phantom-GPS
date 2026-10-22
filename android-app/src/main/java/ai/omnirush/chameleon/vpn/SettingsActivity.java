package ai.omnirush.chameleon.vpn;

import ai.omnirush.chameleon.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Settings tab: Features (NetShield, Split tunneling, Kill switch) and
 * Connection (Protocol, VPN Accelerator) groups. Choices persist via
 * VpnSettings and are applied to every connection.
 */
public class SettingsActivity extends Activity {

    private TextView valNetshield;
    private TextView valSplitTunnel;
    private TextView valProtocol;
    private Switch switchSplitTunnel;
    private Switch switchKillSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        valNetshield = findViewById(R.id.val_netshield);
        valSplitTunnel = findViewById(R.id.val_splittunnel);
        valProtocol = findViewById(R.id.val_protocol);
        switchSplitTunnel = findViewById(R.id.switch_splittunnel);
        switchKillSwitch = findViewById(R.id.switch_killswitch);

        LinearLayout rowNetshield = findViewById(R.id.row_netshield);
        rowNetshield.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showNetShieldDialog(); }
        });

        LinearLayout rowSplit = findViewById(R.id.row_splittunnel);
        rowSplit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                switchSplitTunnel.toggle();
            }
        });

        switchSplitTunnel.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        VpnSettings.setSplitTunnel(SettingsActivity.this, on);
                        refresh();
                    }
                });

        switchKillSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        VpnSettings.setKillSwitch(SettingsActivity.this, on);
                        if (on) {
                            Toast.makeText(SettingsActivity.this,
                                    "Kill switch on — enable Always-on VPN in system settings for lockdown",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });

        LinearLayout rowProtocol = findViewById(R.id.row_protocol);
        rowProtocol.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showProtocolDialog(); }
        });

        BottomNavHelper.wire(this, BottomNavHelper.TAB_SETTINGS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        VpnConfig.NetShieldLevel ns = VpnSettings.getNetShield(this);
        valNetshield.setText(VpnSettings.netShieldLabel(ns));
        boolean st = VpnSettings.isSplitTunnel(this);
        valSplitTunnel.setText(st ? "On" : "Off");
        if (switchSplitTunnel.isChecked() != st) switchSplitTunnel.setChecked(st);
        boolean ks = VpnSettings.isKillSwitch(this);
        if (switchKillSwitch.isChecked() != ks) switchKillSwitch.setChecked(ks);
        valProtocol.setText(VpnSettings.protocolLabel(VpnSettings.getProtocol(this)));
    }

    private void showNetShieldDialog() {
        final String[] labels = {"Off", "Block malware", "Block ads & malware",
                "Block ads, malware & adult"};
        final VpnConfig.NetShieldLevel[] values = {
                VpnConfig.NetShieldLevel.OFF, VpnConfig.NetShieldLevel.MALWARE,
                VpnConfig.NetShieldLevel.ADS_MALWARE,
                VpnConfig.NetShieldLevel.ADS_MALWARE_ADULT};
        VpnConfig.NetShieldLevel current = VpnSettings.getNetShield(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("NetShield")
                .setSingleChoiceItems(labels, checked,
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                VpnSettings.setNetShield(SettingsActivity.this, values[which]);
                                refresh();
                                d.dismiss();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showProtocolDialog() {
        final String[] labels = {"Smart (auto)", "WireGuard UDP", "WireGuard TCP",
                "WireGuard TLS"};
        final String[] values = {"smart", "wg-udp", "wg-tcp", "wg-tls"};
        String current = VpnSettings.getProtocol(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Protocol")
                .setSingleChoiceItems(labels, checked,
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                VpnSettings.setProtocol(SettingsActivity.this, values[which]);
                                refresh();
                                d.dismiss();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
